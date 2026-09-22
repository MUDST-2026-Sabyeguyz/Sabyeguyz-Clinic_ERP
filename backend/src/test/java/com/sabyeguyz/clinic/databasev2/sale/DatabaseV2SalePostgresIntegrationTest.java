package com.sabyeguyz.clinic.databasev2.sale;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class DatabaseV2SalePostgresIntegrationTest {

    private JdbcTemplate jdbc;
    private DatabaseV2SaleRepository repository;

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
            repository = new DatabaseV2SaleRepository(jdbc);

            seedDependencies();
        } catch (Exception e) {
            Assumptions.abort("PostgreSQL not available on " + host + ":" + port + ": " + e.getMessage());
        }
    }

    private void seedDependencies() {
        jdbc.execute("""
            insert into app_user (user_id, username, password_hash, user_status, last_login_at, user_create_at)
            values (1, 'system_sale_test', 'hash', true, current_timestamp, current_timestamp)
            on conflict (user_id) do nothing
        """);

        jdbc.execute("""
            insert into branch (branch_id, branch_code, branch_name, address, phone, branch_status, branch_create_by)
            values (99, '99', 'Sale Test Branch', '123 Sale Rd', '020000099', true, 1)
            on conflict (branch_id) do update set branch_code = '99'
        """);

        jdbc.execute("""
            insert into staff (staff_id, staff_code, staff_fname, staff_lname, position, branch_id, staff_status, hire_date, staff_create_by)
            values (99, 'ST-SALE-99', 'SaleStaff', 'Smith', 'Therapist', 99, true, '2026-01-01', 1)
            on conflict (staff_id) do nothing
        """);

        jdbc.execute("""
            insert into service (service_id, service_code, service_name, service_type, service_price, service_status, default_total_visits, default_bonus_visits, default_validity_days, service_create_by)
            values (99, 'CRS-SALE-99', 'Course Test 10', 'Course', 3000.00, true, 10, 0, 365, 1)
            on conflict (service_id) do nothing
        """);

        jdbc.execute("""
            insert into patient (patient_id, hn, customer_type, title, patient_fname, patient_lname, gender, phone, branch_id, registered_by)
            values (99, '2699099998', 'Thai', 'นาย', 'PatientSale', 'Test', 'Male', '0899999998', 99, 1)
            on conflict (patient_id) do nothing
        """);

        jdbc.execute("""
            insert into payment_method (payment_method_id, payment_method_name, payment_method_status, payment_method_create_by)
            values (99, 'CASH', true, 1)
            on conflict (payment_method_id) do nothing
        """);
    }

    @Test
    void generatesUniqueReceiptNumbersUnderHighConcurrency() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        Set<String> receiptNumbers = ConcurrentHashMap.newKeySet();
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    DatabaseV2SaleRepository.GeneratedReceipt allocated = repository.allocateReceipt();
                    receiptNumbers.add(allocated.receiptNo());
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
        assertThat(receiptNumbers).hasSize(threadCount);

        for (String no : receiptNumbers) {
            assertThat(no).matches("^RE-[0-9]{7}$");
        }
    }

    @Test
    void saleCreationAndAtomicVoidLifecycleAgainstLivePostgres() {
        DatabaseV2SaleRepository.GeneratedReceipt allocated = repository.allocateReceipt();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // 1. Insert receipt
        repository.insertReceipt(allocated.id(), allocated.receiptNo(), 99L, 99L, today, 1L);

        // 2. Insert sales transaction & course
        Long txId = repository.createSalesTransaction(
                99L, 99L, 99L, "CoursePurchase",
                null, 99L,
                1, new BigDecimal("3000.00"), new BigDecimal("3000.00"),
                now, null, 1L
        );
        assertThat(txId).isNotNull();

        Long courseId = repository.createCourse(
                allocated.receiptNo(), "Course Test 10", new BigDecimal("3000.00"),
                10, 0, 365,
                99L, allocated.id(), today,
                99L, 99L, 1L
        );
        assertThat(courseId).isNotNull();
        repository.updateTransactionCourseId(txId, courseId);

        // 3. Insert payment
        Long pId = repository.createPaymentTransaction(
                99L, 99L, txId, new BigDecimal("3000.00"), 99L, now, 1L
        );
        assertThat(pId).isNotNull();

        // 4. Verify findById returns active sale
        Optional<DatabaseV2SaleRepository.SaleRecord> saleOpt = repository.findById(allocated.id());
        assertThat(saleOpt).isPresent();
        DatabaseV2SaleRepository.SaleRecord sale = saleOpt.get();
        assertThat(sale.status()).isEqualTo("PAID");
        assertThat(sale.total()).isEqualByComparingTo("3000.00");
        assertThat(sale.paid()).isEqualByComparingTo("3000.00");
        assertThat(sale.items()).hasSize(1);
        assertThat(sale.payments()).hasSize(1);
        assertThat(repository.isVoided(allocated.id())).isFalse();

        // 5. Void the receipt
        repository.voidReceipt(allocated.id(), "Testing refund flow", 1L);

        // 6. Verify findById now returns voided sale
        Optional<DatabaseV2SaleRepository.SaleRecord> voidedOpt = repository.findById(allocated.id());
        assertThat(voidedOpt).isPresent();
        DatabaseV2SaleRepository.SaleRecord voided = voidedOpt.get();
        assertThat(voided.status()).isEqualTo("VOIDED");
        assertThat(voided.total()).isEqualByComparingTo("3000.00");
        assertThat(voided.paid()).isEqualByComparingTo("0.00");
        assertThat(voided.voidReason()).isEqualTo("Testing refund flow");
        assertThat(voided.voidedAt()).isNotNull();
        assertThat(repository.isVoided(allocated.id())).isTrue();

        // Check payment rows: 1 original + 1 refund
        assertThat(voided.payments()).hasSize(2);
        assertThat(voided.payments().get(0).type()).isEqualTo("PAYMENT");
        assertThat(voided.payments().get(1).type()).isEqualTo("REFUND");
        assertThat(voided.payments().get(1).amount()).isEqualByComparingTo("-3000.00");

        // Verify course status in Postgres is Cancelled
        String courseStatus = jdbc.queryForObject(
                "select cast(course_status as varchar) from course where course_id = ?",
                String.class, courseId
        );
        assertThat(courseStatus).isEqualTo("Cancelled");

        // Verify course_ledger adjustment row exists
        Integer ledgerCount = jdbc.queryForObject(
                "select count(*) from course_ledger where course_id = ? and entry_type = 'Adjustment'",
                Integer.class, courseId
        );
        assertThat(ledgerCount).isEqualTo(1);
    }
}
