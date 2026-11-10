package com.sabyeguyz.clinicv2.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.api.configuration.Configuration;
import org.junit.jupiter.api.Test;

class DemoSeedPolicyTest {
    @Test
    void missingOrNoneModeRunsOnlySchemaMigrations() {
        assertEquals("none", DemoSeedPolicy.resolve(null, "unspecified", ""));
        assertEquals("none", DemoSeedPolicy.resolve(" ", "unspecified", "prod"));
        assertArrayEquals(new String[] {"classpath:db/migration"}, DemoSeedPolicy.locations("none"));
        assertArrayEquals(new String[] {"classpath:db/migration", "classpath:db/seed_v2_legacy_demo"},
                DemoSeedPolicy.locations("full", false));
        assertArrayEquals(new String[] {"classpath:db/migration", "classpath:db/seed_v2_legacy"},
                DemoSeedPolicy.locations("full", true));
        assertTrue(DemoSeedPolicy.useLegacyFullSeed(true, false));
        assertFalse(DemoSeedPolicy.useLegacyFullSeed(true, true));
        assertFalse(DemoSeedPolicy.useLegacyFullSeed(false, false));
    }

    @Test
    void demoModeRequiresExplicitLocalDemoKindAndRejectsProductionProfiles() {
        assertEquals("full", DemoSeedPolicy.resolve(" FULL ", "local-demo", "docker,v2"));
        assertThrows(IllegalStateException.class,
                () -> DemoSeedPolicy.resolve("full", "unspecified", "docker,v2"));
        assertThrows(IllegalStateException.class,
                () -> DemoSeedPolicy.resolve("minimal", "local-demo", "production,v2"));
        assertThrows(IllegalArgumentException.class,
                () -> DemoSeedPolicy.resolve("auto", "local-demo", ""));
    }

    @Test
    void existingSeedModeMustMatchAndConflictingHistoriesFailClosed() {
        assertEquals("full", DatabaseMigrations.resolveExistingMode("full", false, true, true));
        assertEquals("none", DatabaseMigrations.resolveExistingMode("none", false, false, true));
        assertThrows(IllegalStateException.class,
                () -> DatabaseMigrations.resolveExistingMode("none", false, true, true));
        assertThrows(IllegalStateException.class,
                () -> DatabaseMigrations.resolveExistingMode("full", true, true, false));
        assertThrows(IllegalStateException.class,
                () -> DatabaseMigrations.resolveExistingMode("full", false, false, true));
    }

    @Test
    void bearingSeedIsOrderedAndGuardsItsParentRows() throws IOException {
        String seed = readResource("db/seed_v2_legacy/V1102__bearing_branch_mock_data.sql");
        assertTrue(seed.contains("INSERT INTO patient"));
        assertTrue(seed.contains("INSERT INTO hn_sequence"));
        assertTrue(seed.contains("INSERT INTO appointment"));
        assertTrue(seed.contains("ON CONFLICT (patient_hn) DO NOTHING"));
        assertTrue(seed.contains("ON CONFLICT (branch_id, year_month)"));
        assertTrue(seed.contains("WHERE NOT EXISTS ("));
        assertTrue(seed.contains("JOIN patient p ON p.patient_hn = d.hn"));
        assertTrue(seed.contains("JOIN service s"));
        assertTrue(seed.contains("JOIN room r"));
        assertTrue(DemoSeedPolicy.locations("full", false)[1].endsWith("db/seed_v2_legacy_demo"));
        assertTrue(readResource("db/seed_v2_legacy_demo/V1100__legacy_demo_data.sql").contains("INSERT INTO appointment"));
        String conditionSeed = readResource("db/seed_v2_legacy_demo/V1101__all_table_condition_demo.sql");
        assertTrue(conditionSeed.contains("INSERT INTO"));
        assertFalse(conditionSeed.contains("treatment_fee_rule_status"),
                "V1012 removes the status column before demo seeds run");
        assertTrue(readResource("db/seed_v2_legacy_demo/V1100__legacy_demo_data.sql")
                .contains("rule_name, rule_version"));
        assertTrue(readResource("db/seed_v2_legacy_demo/V1103__bearing_demo_seed_variant.sql").contains("SELECT 1"));
    }

    @Test
    void databaseInspectionFailuresStopBeforeMigration() throws SQLException {
        Configuration configuration = mock(Configuration.class);
        DataSource dataSource = mock(DataSource.class);
        when(configuration.getDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenThrow(new SQLException("test database unavailable"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DatabaseMigrations.migrate(configuration, "none", "unspecified", ""));
        assertTrue(error.getMessage().contains("database was not changed"));
    }

    private static String readResource(String name) throws IOException {
        try (InputStream stream = DemoSeedPolicyTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream, "missing resource " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
