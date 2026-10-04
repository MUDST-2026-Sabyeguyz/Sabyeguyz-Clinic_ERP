package com.sabyeguyz.clinic.databasev2.common;

import org.junit.jupiter.api.Assumptions;

/** Shared setup-failure policy for PostgreSQL integration tests. */
public final class PostgresIntegrationTestSupport {

    private static final String REQUIRED_ENV = "PG_INTEGRATION_REQUIRED";

    private PostgresIntegrationTestSupport() {}

    public static void handleSetupFailure(String message, Exception cause) {
        if (Boolean.parseBoolean(System.getenv().getOrDefault(REQUIRED_ENV, "false"))) {
            throw new AssertionError(message, cause);
        }
        Assumptions.abort(message + ": " + cause.getMessage());
    }

    public static void handleSetupFailure(boolean required, String message, Exception cause) {
        if (required) {
            throw new AssertionError(message, cause);
        }
        Assumptions.abort(message + ": " + cause.getMessage());
    }
}
