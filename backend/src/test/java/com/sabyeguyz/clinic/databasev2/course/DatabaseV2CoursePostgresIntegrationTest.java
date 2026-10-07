package com.sabyeguyz.clinic.databasev2.course;

import com.sabyeguyz.clinic.databasev2.common.PostgresIntegrationTestSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.assertj.core.api.SoftAssertions;

import com.sabyeguyz.clinic.course.CourseBalanceException;
import com.sabyeguyz.clinic.databasev2.commission.DatabaseV2CommissionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DatabaseV2CoursePostgresIntegrationTest {

    private JdbcTemplate jdbc;
    private DatabaseV2CourseRepository repository;
    private TransactionTemplate transactionTemplate;

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
            transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(ds));
            jdbc.execute("select 1");
            repository = new DatabaseV2CourseRepository(jdbc);

            seedDependencies();
        } catch (Exception e) {
            PostgresIntegrationTestSupport.handleSetupFailure("PostgreSQL integration setup failed on " + host + ":" + port, e);
        }
    }

    private void seedDependencies() {
        jdbc.execute("""
            insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
            values (1, 'system_course_test', 'hash', true, current_timestamp, current_timestamp)
            on conflict (user_id) do nothing
        """);

        jdbc.execute("""
            insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
            values (99, '99', 'Course Test Branch', '123 Course Rd', '020000099', true, 1)
            on conflict (branch_id) do update set branch_code = '99'
        """);

        jdbc.execute("""
            insert into staff (staff_id, staff_code, staff_fname, staff_lname, position, branch_id, staff_status, hire_date, staff_create_by)
            values (99, 'ST-CRS-99', 'CourseDoc', 'Smith', 'Therapist', 99, true, '2026-01-01', 1)
            on conflict (staff_id) do nothing
        """);

        jdbc.execute("""
            insert into staff (staff_id, staff_code, staff_fname, staff_lname, position, branch_id, staff_status, hire_date, staff_create_by)
            values (98, 'ST-CRS-98', 'CourseOwner', 'Jones', 'Therapist', 99, true, '2026-01-01', 1)
            on conflict (staff_id) do nothing
        """);

        jdbc.execute("""
            insert into service (service_id, service_code, service_name, service_type, service_price, service_status, default_total_visits, default_bonus_visits, default_validity_days, service_create_by)
            values (98, 'CRS-INTEG-10', 'Concurrent Course 10', 'Course', 5000.00, true, 10, 0, 365, 1)
            on conflict (service_id) do nothing
        """);

        jdbc.execute("""
            insert into patient (patient_id, hn, customer_type, title, patient_fname, patient_lname, gender, phone, branch_id, registered_by)
            values (91, '2699099991', 'Thai', 'นาย', 'PatientA', 'Test', 'Male', '0899999991', 99, 1)
            on conflict (patient_id) do nothing
        """);

        jdbc.execute("""
            insert into patient (patient_id, hn, customer_type, title, patient_fname, patient_lname, gender, phone, branch_id, registered_by)
            values (92, '2699099992', 'Thai', 'นาง', 'PatientB', 'Test', 'Female', '0899999992', 99, 1)
            on conflict (patient_id) do nothing
        """);
    }

    @Test
    void concurrentUsageNeverAllowsNegativeBalance() throws InterruptedException {
        // Purchase a course with exactly 10 sessions
        DatabaseV2CourseRepository.CoursePurchaseRecord purchased = repository.purchase(
                91L, 98L, 99L, "99", 99L, 1L
        );
        Long courseId = purchased.id();
        assertThat(purchased.remainingVisits()).isEqualTo(10);

        // Run 15 concurrent threads trying to consume 1 session each
        int threadCount = 15;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    transactionTemplate.executeWithoutResult(status ->
                            repository.consumeSession(courseId, null, "Concurrent test usage", 99L, 1L));
                    successCount.incrementAndGet();
                } catch (CourseBalanceException e) {
                    failureCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        boolean finished = executor.awaitTermination(15, TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failureCount.get()).isEqualTo(5);

        // Verify remaining visits in Postgres database is exactly 0
        DatabaseV2CourseRepository.CoursePurchaseRecord finalCourse = repository.findById(courseId).orElseThrow();
        assertThat(finalCourse.remainingVisits()).isEqualTo(0);
        assertThat(finalCourse.visitsUsed()).isEqualTo(10);

        // Verify ledger entries count: 1 purchase + 10 treatments = 11 entries
        List<DatabaseV2CourseRepository.CourseMovementRecord> movements = repository.findMovements(courseId);
        assertThat(movements).hasSize(11);
        assertThat(movements.get(movements.size() - 1).balanceAfter()).isEqualTo(0);
    }

    @Test
    void usageFailureRollsBackBalanceLedgerAndVisitTogether() {
        var course = repository.purchase(91L, 98L, 99L, "99", 99L, 1L);
        int visitsBefore = jdbc.queryForObject("select count(*) from patient_visit where patient_id=?", Integer.class, 91L);
        String trigger = "course_usage_fail_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String function = trigger + "_fn";
        jdbc.execute(("create function %s() returns trigger language plpgsql as $$ begin "
                + "raise exception 'course usage rollback injection'; end $$").formatted(function));
        jdbc.execute(("create trigger %s before insert on course_visit_usage for each row execute function %s()").formatted(trigger, function));
        try {
            assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                    repository.consumeSession(course.id(), null, "injected usage write failure", 99L, 1L)))
                    .isInstanceOf(RuntimeException.class);
            var after = repository.findById(course.id()).orElseThrow();
            assertThat(after.remainingVisits()).isEqualTo(10);
            assertThat(after.visitsUsed()).isZero();
            assertThat(jdbc.queryForObject("select count(*) from course_ledger where course_id=? and entry_type='Treatment'", Integer.class, course.id())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from course_visit_usage where course_id=?", Integer.class, course.id())).isZero();
            assertThat(jdbc.queryForObject("select count(*) from patient_visit where patient_id=?", Integer.class, 91L)).isEqualTo(visitsBefore);
        } finally {
            jdbc.execute(("drop trigger if exists %s on course_visit_usage").formatted(trigger));
            jdbc.execute(("drop function if exists %s()").formatted(function));
        }
    }

    @Test
    void patientCourseHistoryIsPagedAndDoesNotReturnAnotherPatientsCourses() {
        var patientACourse = repository.purchase(91L, 98L, 99L, "99", 99L, 1L);
        var patientBCourse = repository.purchase(92L, 98L, 99L, "99", 99L, 1L);

        var patientAFirstPage = repository.findByPatientId(91L, 1, 0);
        var patientAFirstPageReload = repository.findByPatientId(91L, 1, 0);
        int patientATotal = repository.countByPatientId(91L);
        var patientBHistory = repository.findByPatientId(92L, 100, 0);

        assertThat(patientAFirstPage).hasSize(1);
        assertThat(patientAFirstPage.get(0).id()).isEqualTo(patientACourse.id());
        assertThat(patientAFirstPage.get(0).patientId()).isEqualTo(91L);
        assertThat(patientAFirstPageReload).extracting(DatabaseV2CourseRepository.CoursePurchaseRecord::id)
                .containsExactlyElementsOf(patientAFirstPage.stream().map(DatabaseV2CourseRepository.CoursePurchaseRecord::id).toList());
        assertThat(patientATotal).isGreaterThanOrEqualTo(1);
        assertThat(patientBHistory).extracting(DatabaseV2CourseRepository.CoursePurchaseRecord::id)
                .contains(patientBCourse.id()).doesNotContain(patientACourse.id());
        assertThat(repository.findByPatientId(91L, 100, 0))
                .allSatisfy(course -> assertThat(course.patientId()).isEqualTo(91L));
    }

    @Test
    void ownerTreatingTheirOwnCourseKeepsTheFullLockedAllocation() {
        DatabaseV2CourseRepository.CoursePurchaseRecord purchased = repository.purchase(
                91L, 98L, 99L, "99", 99L, 1L
        );
        jdbc.update("""
                update course
                set commission_status = 'Locked', locked_commission_rate = 0.14,
                    total_course_commission_pool = 700.00,
                    commission_allocation_per_visit = 70.00
                where course_id = ?
                """, purchased.id());
        var lockedCourse = repository.findById(purchased.id()).orElseThrow();
        DatabaseV2CommissionRepository commissions = new DatabaseV2CommissionRepository(jdbc);
        commissions.createFeeRule(
                99L, "Fixed", new BigDecimal("45.00"), null, LocalDate.now(), null, 1L
        );

        repository.consumeSession(purchased.id(), null, "locked allocation check", 99L, 1L);

        assertThat(purchased.caseOwnerStaffId()).isEqualTo(99L);
        assertThat(lockedCourse.commissionStatus()).isEqualTo("Locked");
        assertThat(lockedCourse.commissionAllocationPerVisit()).isEqualByComparingTo("70.00");

        var usage = jdbc.queryForMap("""
                select treating_staff_id, case_owner_staff_id, gross_commission_allocation,
                       treatment_fee_rule_id, substitute_treatment_fee_amount,
                       owner_net_commission_released
                from course_visit_usage where course_id = ?
                """, purchased.id());
        assertThat(((Number) usage.get("treating_staff_id")).longValue()).isEqualTo(99L);
        assertThat(((Number) usage.get("case_owner_staff_id")).longValue()).isEqualTo(99L);
        assertThat(usage.get("treatment_fee_rule_id")).isNull();
        assertThat((BigDecimal) usage.get("substitute_treatment_fee_amount")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) usage.get("owner_net_commission_released")).isEqualByComparingTo("70.00");
        assertThat((BigDecimal) usage.get("gross_commission_allocation")).isEqualByComparingTo("70.00");
    }

    @Test
    void substituteTreatmentUsesEffectiveRuleAndPreservesUsageHistory() {
        DatabaseV2CourseRepository.CoursePurchaseRecord purchased = repository.purchase(
                91L, 98L, 99L, "99", 98L, 1L
        );
        jdbc.update("""
                update course
                set commission_status = 'Locked', locked_commission_rate = 0.14,
                    total_course_commission_pool = 700.00,
                    commission_allocation_per_visit = 70.00
                where course_id = ?
                """, purchased.id());
        var lockedCourse = repository.findById(purchased.id()).orElseThrow();
        DatabaseV2CommissionRepository commissions = new DatabaseV2CommissionRepository(jdbc);
        var rule = commissions.createFeeRule(
                99L, "Fixed", new BigDecimal("45.00"), null, LocalDate.now(), null, 1L
        );

        repository.consumeSession(purchased.id(), null, "substitute locked allocation check", 99L, 1L);

        var usage = jdbc.queryForMap("""
                select treating_staff_id, case_owner_staff_id, gross_commission_allocation,
                       treatment_fee_rule_id, substitute_treatment_fee_amount,
                       owner_net_commission_released
                from course_visit_usage where course_id = ?
                """, purchased.id());
        SoftAssertions softly = new SoftAssertions();
        softly.assertThat(purchased.caseOwnerStaffId()).isEqualTo(98L);
        softly.assertThat(lockedCourse.commissionStatus()).isEqualTo("Locked");
        softly.assertThat(lockedCourse.commissionAllocationPerVisit()).isEqualByComparingTo("70.00");
        softly.assertThat(((Number) usage.get("treating_staff_id")).longValue()).isEqualTo(99L);
        softly.assertThat(((Number) usage.get("case_owner_staff_id")).longValue()).isEqualTo(98L);
        softly.assertThat(usage.get("treatment_fee_rule_id")).isEqualTo(rule.id());
        softly.assertThat((BigDecimal) usage.get("substitute_treatment_fee_amount")).isEqualByComparingTo("45.00");
        softly.assertThat((BigDecimal) usage.get("owner_net_commission_released")).isEqualByComparingTo("25.00");

        var activeRule = commissions.listFeeRules().stream()
                .filter(candidate -> candidate.id().equals(rule.id()) && candidate.active())
                .findFirst().orElseThrow();
        softly.assertThat(activeRule.employeeId()).isEqualTo(99L);
        softly.assertThat(activeRule.feeValue()).isEqualByComparingTo("45.00");
        softly.assertThat(activeRule.effectiveFrom()).isEqualTo(LocalDate.now());
        softly.assertThat(activeRule.effectiveTo()).isNull();

        commissions.deactivateFeeRule(rule.id(), 1L);
        var historicalUsage = jdbc.queryForMap("""
                select treatment_fee_rule_id, substitute_treatment_fee_amount, owner_net_commission_released
                from course_visit_usage where course_id = ?
                """, purchased.id());
        softly.assertThat(historicalUsage.get("treatment_fee_rule_id")).isEqualTo(rule.id());
        softly.assertThat((BigDecimal) historicalUsage.get("substitute_treatment_fee_amount")).isEqualByComparingTo("45.00");
        softly.assertThat((BigDecimal) historicalUsage.get("owner_net_commission_released")).isEqualByComparingTo("25.00");
        softly.assertThat(commissions.listFeeRules().stream().filter(candidate -> candidate.id().equals(rule.id()))
                .findFirst().orElseThrow().active()).isFalse();
        softly.assertThat(jdbc.queryForObject("select count(*) from course_visit_usage where course_id = ?", Integer.class, purchased.id()))
                .isEqualTo(1);
        softly.assertThat((BigDecimal) usage.get("gross_commission_allocation")).isEqualByComparingTo("70.00");
        softly.assertAll();
    }

    @Test
    void transferCourseSplitsSessionsCorrectly() {
        // 1. Purchase course with 10 sessions for Patient 91
        DatabaseV2CourseRepository.CoursePurchaseRecord source = repository.purchase(
                91L, 98L, 99L, "99", 99L, 1L
        );
        assertThat(source.remainingVisits()).isEqualTo(10);

        // 2. Transfer 4 sessions from Patient 91 to Patient 92
        DatabaseV2CourseRepository.CourseTransferRecord transfer = repository.transfer(
                source.id(), 92L, 4, "Gift for sister", 1L
        );

        assertThat(transfer.id()).isNotNull();
        assertThat(transfer.quantity()).isEqualTo(4);
        assertThat(transfer.sourcePatientId()).isEqualTo(91L);
        assertThat(transfer.destinationPatientId()).isEqualTo(92L);

        // 3. Verify Source Course has 6 remaining
        DatabaseV2CourseRepository.CoursePurchaseRecord updatedSource = repository.findById(source.id()).orElseThrow();
        assertThat(updatedSource.remainingVisits()).isEqualTo(6);

        // 4. Verify Destination Course has 4 remaining
        DatabaseV2CourseRepository.CoursePurchaseRecord destCourse = repository.findById(transfer.destinationPurchaseId()).orElseThrow();
        assertThat(destCourse.remainingVisits()).isEqualTo(4);
        assertThat(destCourse.patientId()).isEqualTo(92L);

        // 5. Verify Transfer History
        List<DatabaseV2CourseRepository.CourseTransferRecord> history = repository.findTransferHistory(91L);
        assertThat(history).isNotEmpty();
        assertThat(history.get(0).id()).isEqualTo(transfer.id());
    }

    @Test
    void reverseUsageAndCancelLifecycle() {
        DatabaseV2CourseRepository.CoursePurchaseRecord course = repository.purchase(
                91L, 98L, 99L, "99", 99L, 1L
        );

        // 1. Consume 1 session
        DatabaseV2CourseRepository.CoursePurchaseRecord used = repository.consumeSession(
                course.id(), null, "Usage before reverse", 99L, 1L
        );
        assertThat(used.remainingVisits()).isEqualTo(9);

        // Find movement ID
        List<DatabaseV2CourseRepository.CourseMovementRecord> movements = repository.findMovements(course.id());
        Long usageMovementId = movements.stream()
                .filter(m -> "Treatment".equals(m.entryType()))
                .findFirst().orElseThrow().id();

        // 2. Reverse usage
        DatabaseV2CourseRepository.CoursePurchaseRecord reversed = repository.reverseUsage(
                usageMovementId, "Accidental deduction", 1L
        );
        assertThat(reversed.remainingVisits()).isEqualTo(10);

        // 3. Cancel course
        DatabaseV2CourseRepository.CoursePurchaseRecord cancelled = repository.cancel(
                course.id(), "Customer requested cancel", 1L
        );
        assertThat(cancelled.status()).isEqualTo("Cancelled");
        assertThat(cancelled.remainingVisits()).isEqualTo(0);
    }
}
