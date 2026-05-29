package com.lms;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class LmsApplicationModulesTest {

    @Test
    void verifiesModularStructure() {
        ApplicationModules.of(LmsApplication.class).verify();
    }
}
