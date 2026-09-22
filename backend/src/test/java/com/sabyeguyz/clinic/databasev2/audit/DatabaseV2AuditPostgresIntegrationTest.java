package com.sabyeguyz.clinic.databasev2.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseV2AuditPostgresIntegrationTest {

    private JdbcTemplate jdbc;
    private DatabaseV2AuditRepository repository;

    @BeforeEach
    void setUp() {
        String host = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
        String port = System.getenv().getOrDefault("POSTGRES_PORT", "5432");
        String db = System.getenv().getOrDefault("POSTGRES_DB", "clinic_erp");
        String user = System.getenv().getOrDefault("POSTGRES_USER", "clinic");
        String password = System.getenv().getOrDefault("POSTGRES_PASSWORD", "local-verification-password");

        try {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName("org.postgresql.Driver");
            ds.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + db);
            ds.setUsername(user);
            ds.setPassword(password);

            jdbc = new JdbcTemplate(ds);
            jdbc.execute("select 1");
            repository = new DatabaseV2AuditRepository(jdbc);

            seedDependencies();
        } catch (Exception e) {
            Assumptions.abort("PostgreSQL not available on " + host + ":" + port + ": " + e.getMessage());
        }
    }

    private void seedDependencies() {
        jdbc.execute("""
            insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
            values (1, 'system_audit_test', 'hash', true, current_timestamp, current_timestamp)
            on conflict (user_id) do nothing
        """);
    }

    @Test
    void canInsertAndListAuditLogs() {
        repository.log("course", "999", "INSERT", null, "{'name':'Test Course'}", 1L);
        repository.log("course", "999", "UPDATE", "{'status':'Active'}", "{'status':'Completed'}", 1L);

        Page<DatabaseV2AuditLogDto> page = repository.list(0, 50);
        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(2);
        assertThat(page.getContent()).isNotEmpty();

        DatabaseV2AuditLogDto top = page.getContent().get(0);
        assertThat(top.auditId()).isNotNull();
        assertThat(top.id()).isEqualTo(top.auditId());
        assertThat(top.changedBy()).isEqualTo(1L);
        assertThat(top.actorId()).isEqualTo(1L);
        assertThat(top.changedAt()).isNotNull();
    }
}
