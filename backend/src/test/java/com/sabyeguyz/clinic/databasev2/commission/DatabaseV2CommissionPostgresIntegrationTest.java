package com.sabyeguyz.clinic.databasev2.commission;

import com.sabyeguyz.clinic.databasev2.common.PostgresIntegrationTestSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sabyeguyz.clinic.databasev2.common.DatabaseV2ConflictException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseV2CommissionPostgresIntegrationTest {

    private JdbcTemplate jdbc;
    private DatabaseV2CommissionRepository repository;

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
            repository = new DatabaseV2CommissionRepository(jdbc);
            seedDependencies();

        } catch (Exception e) {
            PostgresIntegrationTestSupport.handleSetupFailure("PostgreSQL integration setup failed on " + host + ":" + port, e);
        }
    }

    private void seedDependencies() {
        jdbc.execute("""
            insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
            values (1, 'system_comm_test', 'hash', true, current_timestamp, current_timestamp)
            on conflict (user_id) do nothing
        """);

        jdbc.execute("""
            insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
            values (96, '96', 'Comm Test Branch', '123 Comm St', '020000096', true, 1)
            on conflict (branch_id) do update set branch_code = '96'
        """);

        jdbc.execute("""
            insert into staff (staff_id, staff_code, staff_fname, staff_lname, position, branch_id, staff_status, hire_date, staff_create_by)
            values (96, 'ST-COM-96', 'CommSeller', 'Jones', 'Therapist', 96, true, '2026-01-01', 1)
            on conflict (staff_id) do nothing
        """);

        jdbc.execute("""
            insert into service (service_id, service_code, service_name, service_type, service_price, service_status, default_total_visits, default_bonus_visits, default_validity_days, service_create_by)
            values (96, 'SRV-COM-96', 'Commissionable Course', 'Course', 10000.00, true, 10, 0, 365, 1)
            on conflict (service_id) do nothing
        """);

        jdbc.execute("""
            insert into patient (patient_id, hn, customer_type, title, patient_fname, patient_lname, gender, phone, branch_id, registered_by)
            values (96, '2696099996', 'Thai', 'นาย', 'CommPatient', 'Test', 'Male', '0899999996', 96, 1)
            on conflict (patient_id) do nothing
        """);
    }

    @Test
    void tierValidationRejectsOverlappingRanges() {
        List<DatabaseV2CommissionRepository.TierInput> badTiers = List.of(
                new DatabaseV2CommissionRepository.TierInput(BigDecimal.ZERO, new BigDecimal("10000.00"), new BigDecimal("0.05"), 1),
                new DatabaseV2CommissionRepository.TierInput(new BigDecimal("5000.00"), null, new BigDecimal("0.10"), 2)
        );

        assertThatThrownBy(() -> repository.validateTiers(badTiers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ranges must be contiguous and non-overlapping");
    }

    @Test
    void createSchemeAndTiersLifecycle() {
        List<DatabaseV2CommissionRepository.TierInput> tiers = List.of(
                new DatabaseV2CommissionRepository.TierInput(BigDecimal.ZERO, new BigDecimal("50000.00"), new BigDecimal("0.05"), 1),
                new DatabaseV2CommissionRepository.TierInput(new BigDecimal("50000.01"), null, new BigDecimal("0.10"), 2)
        );

        DatabaseV2CommissionRepository.SchemeRecord scheme = repository.createScheme(
                "Standard 2026", "1", LocalDate.of(2026, 1, 1), null, tiers, 1L
        );

        assertThat(scheme.id()).isNotNull();
        assertThat(scheme.name()).isEqualTo("Standard 2026");
        assertThat(scheme.active()).isTrue();

        List<DatabaseV2CommissionRepository.TierRecord> fetchedTiers = repository.listTiers(scheme.id());
        assertThat(fetchedTiers).hasSize(2);
        assertThat(fetchedTiers.get(0).rate()).isEqualByComparingTo("0.05");
        assertThat(fetchedTiers.get(1).rate()).isEqualByComparingTo("0.10");
    }

    @Test
    void monthlyClosingLocksCoursesAndPreventsDoubleClose() {
        int fixtureYear = 3000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 6000);
        LocalDate month = LocalDate.of(fixtureYear, 8, 1);
        String monthStr = String.format("%04d-%02d", month.getYear(), month.getMonthValue());
        LocalDate saleDate = month.plusDays(14);
        DatabaseV2CommissionRepository.SchemeRecord scheme = repository.createScheme(
                "Monthly close fixture " + UUID.randomUUID(), "1", month, null,
                List.of(new DatabaseV2CommissionRepository.TierInput(
                        BigDecimal.ZERO, null, new BigDecimal("0.10"), 1)), 1L
        );

        // Seed receipt and course
        Long receiptId = jdbc.queryForObject("select nextval('receipt_receipt_id_seq')", Long.class);
        String receiptNo = String.format("RE-%07d", receiptId);
        jdbc.update(
                "insert into receipt (receipt_id, receipt_no, patient_id, branch_id, sale_date, receipt_create_at, receipt_create_by) values (?, ?, 96, 96, ?, current_timestamp, 1)",
                receiptId, receiptNo, saleDate
        );

        repository.getOrCreateCommissionPermission(1L);
        Long courseId = jdbc.queryForObject(
                """
                insert into course (
                    course_no, course_sequence, course_name, course_price,
                    total_visits, bonus_visits, expiry_date, course_status,
                    service_id, receipt_id, sale_month, seller_staff_id, case_owner_staff_id,
                    commission_status, commission_scheme_id, commission_scheme_version,
                    visits_used, remaining_visits, course_create_at, course_create_by
                ) values (
                    ?, 1, 'Commission Test Course', 20000.00,
                    10, 0, ?, 'Active',
                    96, ?, ?, 96, 96,
                    'Provisional', ?, 1,
                    0, 10, current_timestamp, 1
                ) returning course_id
                """,
                Long.class, receiptNo, month.plusYears(1), receiptId, monthStr, scheme.id()
        );

        // 1. Perform monthly closing
        List<DatabaseV2CommissionRepository.ClosingRecord> closings = repository.closeMonth(month, 1L);
        assertThat(closings).isNotEmpty();
        DatabaseV2CommissionRepository.ClosingRecord record = closings.stream()
                .filter(c -> Long.valueOf(96L).equals(c.employeeId()))
                .findFirst().orElseThrow();

        assertThat(record.status()).isEqualTo("CLOSED");
        assertThat(record.sales()).isGreaterThanOrEqualTo(new BigDecimal("20000.00"));

        // 2. Verify course is now Locked
        String courseCommStatus = jdbc.queryForObject("select cast(commission_status as varchar) from course where course_id = ?", String.class, courseId);
        assertThat(courseCommStatus).isEqualTo("Locked");

        BigDecimal lockedRate = jdbc.queryForObject("select locked_commission_rate from course where course_id = ?", BigDecimal.class, courseId);
        assertThat(lockedRate).isNotNull();
        var closedSnapshot = jdbc.queryForMap("""
                select monthly_course_sales, commission_scheme_id, commission_scheme_version,
                       calculated_commission_rate, locked_commission_rate, closing_status, closing_at, closed_by
                from monthly_commission_closing where closing_id = ?
                """, record.id());
        var courseSnapshot = jdbc.queryForMap("""
                select locked_commission_rate, total_course_commission_pool,
                       commission_allocation_per_visit, course_update_at
                from course where course_id = ?
                """, courseId);

        // 3. Trying to close again on single record throws conflict
        assertThatThrownBy(() -> repository.closeSingle(record.id(), 1L))
                .isInstanceOf(DatabaseV2ConflictException.class)
                .hasMessageContaining("รอบนี้ปิดแล้ว");

        repository.updateScheme(
                scheme.id(), "Revised monthly fixture", "2", month, null,
                List.of(new DatabaseV2CommissionRepository.TierInput(
                        BigDecimal.ZERO, null, new BigDecimal("0.25"), 1)), true, 1L
        );
        List<DatabaseV2CommissionRepository.ClosingRecord> repeatedClosings = repository.closeMonth(month, 1L);
        DatabaseV2CommissionRepository.ClosingRecord repeated = repeatedClosings.stream()
                .filter(c -> c.id().equals(record.id())).findFirst().orElseThrow();
        assertThat(repeated.status()).isEqualTo("CLOSED");
        assertThat(repeated.rate()).isEqualByComparingTo("0.10");
        assertThat(repeated.version()).isEqualTo(record.version());

        BigDecimal afterReclose = jdbc.queryForObject(
                "select locked_commission_rate from course where course_id = ?", BigDecimal.class, courseId
        );
        assertThat(afterReclose).isEqualByComparingTo("0.10");
        assertThat(jdbc.queryForMap("""
                select monthly_course_sales, commission_scheme_id, commission_scheme_version,
                       calculated_commission_rate, locked_commission_rate, closing_status, closing_at, closed_by
                from monthly_commission_closing where closing_id = ?
                """, record.id())).containsAllEntriesOf(closedSnapshot);
        assertThat(jdbc.queryForMap("""
                select locked_commission_rate, total_course_commission_pool,
                       commission_allocation_per_visit, course_update_at
                from course where course_id = ?
                """, courseId)).containsAllEntriesOf(courseSnapshot);
    }

    @Test
    void feeRuleCreationAndDeactivation() {
        DatabaseV2CommissionRepository.FeeRuleRecord rule = repository.createFeeRule(
                96L, "Fixed", new BigDecimal("350.00"), null, LocalDate.now(), null, 1L
        );
        assertThat(rule.id()).isNotNull();
        assertThat(rule.feeType()).isEqualTo("Fixed");
        assertThat(rule.feeValue()).isEqualByComparingTo("350.00");
        assertThat(rule.active()).isTrue();

        repository.deactivateFeeRule(rule.id(), 1L);

        List<DatabaseV2CommissionRepository.FeeRuleRecord> rules = repository.listFeeRules();
        DatabaseV2CommissionRepository.FeeRuleRecord found = rules.stream().filter(r -> r.id().equals(rule.id())).findFirst().orElseThrow();
        assertThat(found.active()).isFalse();
    }
}
