package com.sabyeguyz.clinic.databasev2.bootstrap;

import com.sabyeguyz.clinic.databasev2.common.PostgresIntegrationTestSupport;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DatabaseV2BootstrapPostgresIntegrationTest {

    private JdbcTemplate jdbc;
    private DatabaseV2BootstrapRunner runner;
    private String adminPassword;

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
            adminPassword = System.getenv("APP_BOOTSTRAP_ADMIN_PASSWORD");
            if (adminPassword == null || adminPassword.isBlank()) {
                PostgresIntegrationTestSupport.handleSetupFailure(
                        Boolean.parseBoolean(System.getenv().getOrDefault("PG_INTEGRATION_REQUIRED", "false")),
                        "APP_BOOTSTRAP_ADMIN_PASSWORD is required for PostgreSQL bootstrap verification",
                        new IllegalStateException("APP_BOOTSTRAP_ADMIN_PASSWORD is missing")
                );
            }
            runner = new DatabaseV2BootstrapRunner(jdbc, "admin", adminPassword);
        } catch (Exception e) {
            PostgresIntegrationTestSupport.handleSetupFailure("PostgreSQL integration setup failed on " + host + ":" + port, e);
        }
    }

    @Test
    void bootstrapIsIdempotentAndSeedsAllRequiredEntities() {
        // Run once
        runner.bootstrap();

        // 1. Verify Admin User
        List<String> hashes = jdbc.queryForList("select password_hash from app_user where username = 'admin'", String.class);
        assertThat(hashes).isNotEmpty();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertThat(encoder.matches(adminPassword, hashes.get(0))).isTrue();

        // 2. Verify Roles
        List<String> roles = jdbc.queryForList("select role_name from role", String.class);
        assertThat(roles).contains("Manager", "Therapist", "Receptionist");

        // 3. Verify Branch
        Integer branchCount = jdbc.queryForObject("select count(*) from branch where branch_code = '01'", Integer.class);
        assertThat(branchCount).isEqualTo(1);

        // 4. Verify Staff linked to Admin
        Long staffId = jdbc.queryForObject("select staff_id from app_user where username = 'admin'", Long.class);
        assertThat(staffId).isNotNull();

        String staffCode = jdbc.queryForObject("select staff_code from staff where staff_id = ?", String.class, staffId);
        assertThat(staffCode).isEqualTo("ST-MGR-01");

        // 5. Verify User Role and Branch Access
        Integer userRoleCount = jdbc.queryForObject(
                "select count(*) from user_role ur join app_user u on u.user_id = ur.user_id where u.username = 'admin'",
                Integer.class
        );
        assertThat(userRoleCount).isGreaterThanOrEqualTo(1);

        Integer branchAccessCount = jdbc.queryForObject(
                "select count(*) from user_branch_access uba join app_user u on u.user_id = uba.user_id where u.username = 'admin'",
                Integer.class
        );
        assertThat(branchAccessCount).isGreaterThanOrEqualTo(1);

        // 6. Verify Payment Methods
        List<String> payMethods = jdbc.queryForList("select payment_method_name from payment_method", String.class);
        assertThat(payMethods).contains("Cash", "CreditCard", "PromptPay");

        // 7. Verify Services
        List<String> serviceCodes = jdbc.queryForList("select service_code from service", String.class);
        assertThat(serviceCodes).contains("SRV-ASSESS", "SRV-SINGLE", "CRS-BASIC-10");

        // 8. Run second time to verify idempotency (no exceptions, no duplicates)
        runner.bootstrap();

        Integer adminCount = jdbc.queryForObject("select count(*) from app_user where username = 'admin'", Integer.class);
        assertThat(adminCount).isEqualTo(1);

        Integer branch01Count = jdbc.queryForObject("select count(*) from branch where branch_code = '01'", Integer.class);
        assertThat(branch01Count).isEqualTo(1);

        Integer staffMgrCount = jdbc.queryForObject("select count(*) from staff where staff_code = 'ST-MGR-01'", Integer.class);
        assertThat(staffMgrCount).isEqualTo(1);
    }
}
