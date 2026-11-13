package com.sabyeguyz.clinicv2.config;

import org.flywaydb.core.Flyway;

/** Dedicated migration entry point: no HTTP server, seed or account provisioning. */
public final class ActionsMigrationMain {
    private ActionsMigrationMain() {}
    public static void main(String[] args) {
        Flyway.configure().dataSource(required("SPRING_DATASOURCE_URL"), required("SPRING_DATASOURCE_USERNAME"),
                required("SPRING_DATASOURCE_PASSWORD"))
                .locations("classpath:db/migration").outOfOrder(true).load().migrate();
    }
    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing migration environment: " + key);
        return value;
    }
}
