package com.sabyeguyz.clinic.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Verifies that the database-team v2 schema was installed in the active
 * PostgreSQL schema. This is intentionally a schema-level bridge: legacy JPA
 * mappings are migrated separately and must not be treated as v2 compatible
 * merely because the application can open a JDBC connection.
 */
@Component
@ConditionalOnProperty(name = "app.database.schema-v2.enabled", havingValue = "true")
public class DatabaseV2HealthIndicator implements HealthIndicator {

    static final Set<String> REQUIRED_TABLES = Set.of(
            "branch", "role", "service", "permission", "payment_method", "room",
            "master_data_type", "staff", "app_user", "user_role", "role_permission",
            "user_branch_access", "master_data_value", "service_group",
            "service_group_member", "staff_group", "staff_group_member", "system_setting",
            "patient", "hn_sequence", "appointment", "appointment_status_history",
            "patient_visit", "commission_scheme", "commission_tier", "treatment_fee_rule",
            "receipt", "course", "course_shared_member", "course_ledger", "course_transfer",
            "course_visit_usage", "sales_transaction", "treatment", "payment_transaction",
            "transaction_cancellation", "monthly_commission_closing",
            "commission_allocation_ledger", "commission_rate_override_audit", "audit_log");

    private final JdbcTemplate jdbcTemplate;

    public DatabaseV2HealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        List<String> rows = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables "
                        + "where table_schema = current_schema() and table_type = 'BASE TABLE'",
                String.class);

        Set<String> missing = new LinkedHashSet<>(REQUIRED_TABLES);
        missing.removeAll(rows);

        if (!missing.isEmpty()) {
            return Health.down()
                    .withDetail("schema", "database-v2")
                    .withDetail("expectedTables", REQUIRED_TABLES.size())
                    .withDetail("missingTables", missing)
                    .build();
        }

        return Health.up()
                .withDetail("schema", "database-v2")
                .withDetail("expectedTables", REQUIRED_TABLES.size())
                .build();
    }
}
