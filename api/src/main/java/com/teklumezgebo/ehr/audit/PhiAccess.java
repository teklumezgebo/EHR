package com.teklumezgebo.ehr.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method that reads or writes PHI. Each call is checked against the access policy and
 * recorded in audit_event, whether it succeeds or is denied.
 *
 * <p>The annotated method must have a {@code UUID} parameter named by {@link #patientParam()}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PhiAccess {

	/** FHIR resource type being accessed, e.g. {@code Patient}. */
	String resourceType();

	/** Audited action, e.g. {@code READ}. */
	String action() default "READ";

	/** Name of the parameter holding the patient id. */
	String patientParam() default "patientId";

	/**
	 * Name of the parameter whose value is recorded as the audited resource_id. Empty records the
	 * patient id. Records nested under an encounter (vitals, notes) name the encounter here.
	 */
	String resourceParam() default "";

}
