package com.sabyeguyz.clinicv2.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.output.MigrateResult;

/** Shared startup/job migration policy. Existing migration files and database contents are never repaired or reset. */
public final class DatabaseMigrations {
    private DatabaseMigrations() {}

    public static MigrateResult migrate(Configuration configuration, String requestedMode,
                                        String databaseKind, String activeProfiles) {
        String selected = DemoSeedPolicy.resolve(requestedMode, databaseKind, activeProfiles);
        SeedSelection selection = selectMode(configuration, selected);
        selected = selection.mode();
        String[] locations = DemoSeedPolicy.locations(selected, selection.legacyFullSeed());
        System.out.println("CLINIC_MIGRATION seedMode=" + selected);
        MigrateResult result = Flyway.configure().configuration(configuration).locations(locations)
                .outOfOrder(true).cleanDisabled(true).load().migrate();
        System.out.println("CLINIC_MIGRATION_OK migrationsExecuted=" + result.migrationsExecuted);
        return result;
    }

    private static SeedSelection selectMode(Configuration configuration, String requestedMode) {
        String mode = DemoSeedPolicy.resolve(requestedMode, "local-demo", "");
        try (Connection connection = configuration.getDataSource().getConnection()) {
            boolean minimal = false;
            boolean full = false;
            boolean bearingFull = false;
            if (tableExists(connection, "flyway_schema_history")) {
                try (var statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery("SELECT version FROM public.flyway_schema_history WHERE success AND version IN ('1000', '1100', '1103')")) {
                    while (rows.next()) {
                        minimal |= "1000".equals(rows.getString(1));
                        full |= "1100".equals(rows.getString(1));
                        bearingFull |= "1103".equals(rows.getString(1));
                    }
                }
            }
            boolean occupied = hasRows(connection, "branch", null) || hasRows(connection, "patient", null)
                    || hasRows(connection, "app_user", "username <> 'system'");
            String selected = resolveExistingMode(mode, minimal, full, occupied);
            return new SeedSelection(selected, "full".equals(selected)
                    && DemoSeedPolicy.useLegacyFullSeed(full, bearingFull));
        } catch (SQLException failure) {
            throw new IllegalStateException("Cannot inspect migration history; database was not changed", failure);
        }
    }

    static String resolveExistingMode(String requestedMode, boolean minimalApplied,
                                      boolean fullApplied, boolean hasApplicationData) {
        if (minimalApplied && fullApplied) {
            throw new IllegalStateException("Both demo seed histories are present; inspect the database before deploying.");
        }
        String existingMode = fullApplied ? "full" : minimalApplied ? "minimal" : null;
        if (existingMode != null && !existingMode.equals(requestedMode)) {
            throw new IllegalStateException("Database already uses seedMode=" + existingMode
                    + "; requested " + requestedMode + ". Use the existing mode for this local demo database or select a new database.");
        }
        if (existingMode == null && hasApplicationData && !"none".equals(requestedMode)) {
            throw new IllegalStateException("Database contains application data without the requested demo history."
                    + " Choose none to preserve it, or explicitly choose a new empty demo database.");
        }
        return existingMode == null ? requestedMode : existingMode;
    }

    private record SeedSelection(String mode, boolean legacyFullSeed) {}

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT to_regclass(?) IS NOT NULL")) {
            statement.setString(1, "public." + table);
            try (ResultSet rows = statement.executeQuery()) { rows.next(); return rows.getBoolean(1); }
        }
    }

    private static boolean hasRows(Connection connection, String table, String predicate) throws SQLException {
        if (!tableExists(connection, table)) return false;
        try (var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT EXISTS (SELECT 1 FROM public." + table
                     + (predicate == null ? "" : " WHERE " + predicate) + ")")) {
            rows.next();
            return rows.getBoolean(1);
        }
    }
}
