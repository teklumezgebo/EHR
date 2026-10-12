package com.teklumezgebo.ehr;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Enforces module boundaries: each direct subpackage of {@code com.teklumezgebo.ehr} is a module,
 * and modules may only reference each other's top-level (API) package, with no cycles.
 */
class ModularityTests {

	private final ApplicationModules modules = ApplicationModules.of(EhrApplication.class);

	@Test
	void verifiesModuleStructure() {
		modules.verify();
	}

}
