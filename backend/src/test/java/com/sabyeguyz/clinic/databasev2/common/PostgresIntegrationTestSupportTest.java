package com.sabyeguyz.clinic.databasev2.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PostgresIntegrationTestSupportTest {

    @Test
    void requiredIntegrationSetupFailureIsAnError() {
        IllegalStateException seedFailure = new IllegalStateException("injected seed failure");

        assertThatThrownBy(() -> PostgresIntegrationTestSupport.handleSetupFailure(
                true, "PostgreSQL integration setup failed", seedFailure))
                .isInstanceOf(AssertionError.class)
                .hasCause(seedFailure)
                .hasMessageContaining("PostgreSQL integration setup failed");
    }
}
