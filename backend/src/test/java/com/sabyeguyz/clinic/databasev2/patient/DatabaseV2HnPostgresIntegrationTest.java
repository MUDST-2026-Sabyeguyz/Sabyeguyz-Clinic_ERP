package com.sabyeguyz.clinic.databasev2.patient;

import com.sabyeguyz.clinic.databasev2.common.PostgresIntegrationTestSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseV2HnPostgresIntegrationTest {

    private JdbcTemplate jdbc;
    private DatabaseV2PatientRepository repository;

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
            repository = new DatabaseV2PatientRepository(jdbc);
        } catch (Exception e) {
            PostgresIntegrationTestSupport.handleSetupFailure("PostgreSQL integration setup failed on " + host + ":" + port, e);
        }
    }

    @Test
    void generatesUniqueHnUnderHighConcurrency() throws InterruptedException {
        // Ensure a test app_user exists for circular FKs
        jdbc.execute("""
            insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
            values (1, 'system_test', 'hash', true, current_timestamp, current_timestamp)
            on conflict (user_id) do nothing
        """);

        // Ensure a test branch exists
        jdbc.execute("""
            insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
            values (99, '99', 'Test Branch 99', '123 Test Rd', '020000099', true, 1)
            on conflict (branch_id) do update set branch_code = '99'
        """);

        LocalDate testDate = LocalDate.of(2026, 9, 1);
        String yearMonth = "2609";

        // Reset sequence for test branch 99 and 2609
        jdbc.update("delete from hn_sequence where branch_id = 99 and year_month = ?", yearMonth);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        Set<String> generatedHns = ConcurrentHashMap.newKeySet();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    String hn = repository.generateHn(99L, "99", testDate);
                    generatedHns.add(hn);
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        boolean completed = executor.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();
        assertThat(generatedHns).hasSize(threadCount);

        for (String hn : generatedHns) {
            assertThat(hn).matches("^269909[0-9]{4}$");
        }

        // Verify last running number in PostgreSQL database
        Integer lastRunning = jdbc.queryForObject(
                "select last_running_no from hn_sequence where branch_id = 99 and year_month = ?",
                Integer.class, yearMonth
        );
        assertThat(lastRunning).isEqualTo(threadCount);
    }

    @Test
    void rejectsHnOverflowWithoutAdvancingOrReusingCounter() {
        jdbc.execute("""
            insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
            values (1, 'system_test', 'hash', true, current_timestamp, current_timestamp)
            on conflict (user_id) do nothing
        """);
        jdbc.execute("""
            insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
            values (99, '99', 'Test Branch 99', '123 Test Rd', '020000099', true, 1)
            on conflict (branch_id) do update set branch_code = '99'
        """);
        String yearMonth = "2610";
        jdbc.update("delete from hn_sequence where branch_id = 99 and year_month = ?", yearMonth);
        jdbc.update("""
            insert into hn_sequence (branch_id, year_month, last_running_no, last_running_at, created_at)
            values (99, ?, 9999, current_timestamp, current_timestamp)
            """, yearMonth);

        assertThatThrownBy(() -> repository.generateHn(99L, "99", LocalDate.of(2026, 10, 5)))
                .isInstanceOf(HnCapacityExceededException.class)
                .hasMessageContaining("HN running number exhausted");
        assertThat(jdbc.queryForObject(
                "select last_running_no from hn_sequence where branch_id = 99 and year_month = ?",
                Integer.class, yearMonth)).isEqualTo(9999);
    }

    @Test
    void partitionsRunningNumbersByBranchAndYearMonth() {
        jdbc.execute("""
            insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
            values (1, 'system_test', 'hash', true, current_timestamp, current_timestamp)
            on conflict (user_id) do nothing
        """);
        jdbc.execute("""
            insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
            values (99, '99', 'Test Branch 99', '123 Test Rd', '020000099', true, 1)
            on conflict (branch_id) do update set branch_code = '99'
        """);
        jdbc.execute("""
            insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
            values (10098, '98', 'Test Branch 98', '98 Test Rd', '020000098', true, 1)
            on conflict (branch_id) do update set branch_code = '98'
        """);
        jdbc.update("delete from hn_sequence where branch_id in (99, 10098) and year_month in ('2609', '2610')");

        assertThat(repository.generateHn(99L, "99", LocalDate.of(2026, 9, 30))).isEqualTo("2699090001");
        assertThat(repository.generateHn(99L, "99", LocalDate.of(2026, 10, 1))).isEqualTo("2699100001");
        assertThat(repository.generateHn(10098L, "98", LocalDate.of(2026, 9, 30))).isEqualTo("2698090001");
    }
}
