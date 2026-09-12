package com.sabyeguyz.clinic.test;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;
import org.testcontainers.containers.PostgreSQLContainer;

/** Starts one disposable PostgreSQL instance for the complete test session. */
public final class PostgresTestEnvironment implements LauncherSessionListener {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sabyeguyz_test")
            .withUsername("sabyeguyz_test")
            .withPassword("test-only-password");

    @Override
    public void launcherSessionOpened(LauncherSession session) {
        POSTGRES.start();
        System.setProperty("DATABASE_URL", POSTGRES.getJdbcUrl());
        System.setProperty("DATABASE_USERNAME", POSTGRES.getUsername());
        System.setProperty("DATABASE_PASSWORD", POSTGRES.getPassword());
        System.setProperty("JWT_SECRET", "test-only-jwt-secret-that-is-at-least-32-chars");
    }

    @Override
    public void launcherSessionClosed(LauncherSession session) {
        POSTGRES.stop();
    }
}
