package com.sabyeguyz.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseV2HealthIndicatorTest {

    private static final String TABLE_QUERY =
            "select table_name from information_schema.tables "
                    + "where table_schema = current_schema() and table_type = 'BASE TABLE'";

    @Test
    void reportsUpWhenEveryV2TableExists() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(eq(TABLE_QUERY), eq(String.class)))
                .thenReturn(new ArrayList<>(DatabaseV2HealthIndicator.REQUIRED_TABLES));

        var health = new DatabaseV2HealthIndicator(jdbcTemplate).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("schema", "database-v2");
        assertThat(health.getDetails()).containsEntry("expectedTables", 40);
    }

    @Test
    void reportsDownAndNamesMissingTables() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        List<String> tables = new ArrayList<>(DatabaseV2HealthIndicator.REQUIRED_TABLES);
        tables.remove("app_user");
        when(jdbcTemplate.queryForList(eq(TABLE_QUERY), eq(String.class))).thenReturn(tables);

        var health = new DatabaseV2HealthIndicator(jdbcTemplate).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails().get("missingTables").toString()).contains("app_user");
    }
}
