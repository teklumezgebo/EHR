package com.teklumezgebo.ehr.audit;

import com.teklumezgebo.ehr.access.AccessPolicy;
import java.util.Arrays;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Enforces the access policy on {@link PhiAccess} methods and audits every call. */
@Aspect
@Component
class PhiAccessAspect {

	private final AccessPolicy accessPolicy;

	private final AuditLog auditLog;

	PhiAccessAspect(AccessPolicy accessPolicy, AuditLog auditLog) {
		this.accessPolicy = accessPolicy;
		this.auditLog = auditLog;
	}

	@Around("@annotation(phiAccess)")
	Object enforce(ProceedingJoinPoint call, PhiAccess phiAccess) throws Throwable {
		Authentication caller = SecurityContextHolder.getContext().getAuthentication();
		UUID patientId = (UUID) argument(call, phiAccess.patientParam());
		String resourceId = phiAccess.resourceParam().isEmpty() ? patientId.toString()
				: String.valueOf(argument(call, phiAccess.resourceParam()));

		// Unknown patients are denied like any other patient outside the care team, so a 403
		// does not reveal whether a record exists.
		if (!accessPolicy.canAccessChart(caller, patientId)) {
			auditLog.record(caller, phiAccess, patientId, resourceId, AuditLog.DENIED);
			throw new AccessDeniedException("Not authorized for this patient");
		}

		// Recorded after the call, so a read or write that throws is logged as ERROR, not SUCCESS.
		Object result;
		try {
			result = call.proceed();
		}
		catch (Throwable failure) {
			try {
				auditLog.record(caller, phiAccess, patientId, resourceId, AuditLog.ERROR);
			}
			catch (RuntimeException auditFailure) {
				failure.addSuppressed(auditFailure);
			}
			throw failure;
		}
		auditLog.record(caller, phiAccess, patientId, resourceId, AuditLog.SUCCESS);
		return result;
	}

	private static Object argument(ProceedingJoinPoint call, String paramName) {
		String[] names = ((MethodSignature) call.getSignature()).getParameterNames();
		int index = Arrays.asList(names).indexOf(paramName);
		if (index < 0) {
			throw new IllegalStateException("@PhiAccess method has no parameter named " + paramName);
		}
		return call.getArgs()[index];
	}

}
