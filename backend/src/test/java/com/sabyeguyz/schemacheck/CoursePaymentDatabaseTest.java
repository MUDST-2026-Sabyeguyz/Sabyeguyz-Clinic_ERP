package com.sabyeguyz.schemacheck;

import com.sabyeguyz.clinicv2.ClinicV2Application;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.commission.MonthlyTierSalesService;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.enums.ReasonCode;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.sale.*;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/** Database-facing course collection contracts; run with mvn test and Docker. */
@Testcontainers
@Timeout(90)
class CoursePaymentDatabaseTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
    private static ConfigurableApplicationContext context;
    private static JdbcTemplate db;
    private static TransactionTemplate tx;
    private Long actorId, patientId, branchId, courseId, methodId;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private static <T> T bean(Class<T> type) { return context.getBean(type); }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static EntityManager em() {
        return EntityManagerFactoryUtils.getTransactionalEntityManager(bean(EntityManagerFactory.class));
    }
    private static <T> T transaction(Supplier<T> body) {
        return tx.execute(status -> body.get());
    }
    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, money(expected).compareTo(actual), "expected " + expected + ", got " + actual);
    }

    @BeforeAll
    static void startApplication() {
        context = SpringApplication.run(ClinicV2Application.class,
                "--spring.profiles.active=v2", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.datasource.hikari.data-source-properties.options=-c lock_timeout=5000",
                "--spring.flyway.locations=classpath:db/migration,classpath:db/seed_v2",
                "--spring.jpa.hibernate.ddl-auto=validate", "--spring.jpa.open-in-view=false",
                "--app.jwt.secret=course-payment-test-only-secret-at-least-32-characters",
                "--app.calendar.enabled=false", "--logging.level.root=WARN");
        db = bean(JdbcTemplate.class);
        tx = new TransactionTemplate(bean(PlatformTransactionManager.class));
    }

    @BeforeEach
    void resetFixture() {
        db.execute("TRUNCATE TABLE receipt RESTART IDENTITY CASCADE");
        db.execute("TRUNCATE TABLE audit_log");
        db.update("update payment_label set payment_label_status=true");
        db.update("update payment_method set payment_method_status=true where method_name='PromptPay'");
        db.update("update branch set branch_status=true where branch_code in ('01','02')");
        ResourceDatabasePopulator fixture = new ResourceDatabasePopulator(
                new ClassPathResource("fixtures/shared-course-refund.sql"));
        fixture.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        fixture.execute(bean(javax.sql.DataSource.class));
        actorId = id("select user_id from app_user where username='manager'");
        patientId = id("select purchaser_patient_id from course order by course_id limit 1");
        branchId = id("select branch_id from branch where branch_code='01'");
        courseId = id("select course_id from course order by course_id limit 1");
        methodId = id("select payment_method_id from payment_method where method_name='PromptPay'");
        authenticate("manager");
    }

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @AfterAll
    static void closeApplication() {
        if (context != null) context.close();
    }

    private static Long id(String sql, Object... parameters) {
        return db.queryForObject(sql, Long.class, parameters);
    }
    private void authenticate(String username) {
        Long userId = id("select user_id from app_user where username=?", username);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), "test", List.of()));
    }
    private List<ReceiptCoursePaymentService.ItemRequest> one(String amount) {
        return List.of(new ReceiptCoursePaymentService.ItemRequest(courseId, money(amount)));
    }
    private CoursePaymentCollectionService.Result collect(
            Long patient, Long branch, List<ReceiptCoursePaymentService.ItemRequest> requested,
            Discount discount, String label, String paid) {
        return transaction(() -> bean(CoursePaymentCollectionService.class).collect(
                em().find(Patient.class, patient), em().find(Branch.class, branch), requested,
                discount, label, em().find(PaymentMethod.class, methodId), money(paid)));
    }
    private CoursePaymentCollectionService.Result collect(String before, Discount discount, String label, String paid) {
        return collect(patientId, branchId, one(before), discount, label, paid);
    }
    private Map<String,Object> counts() {
        return db.queryForMap("select (select count(*) from receipt) as receipts,"
                + "(select count(*) from payment_transaction) as payments,"
                + "(select count(*) from receipt_course_payment) as items,"
                + "(select count(*) from transaction_cancellation) as cancellations,"
                + "(select count(*) from audit_log) as audit");
    }
    private String courseSnapshot() {
        return db.queryForObject("select row_to_json(c)::text from course c where course_id=?", String.class, courseId);
    }
    private List<Map<String,Object>> entitlements() {
        return db.queryForList("select * from course_entitlement where course_id=? order by patient_id", courseId);
    }

    @Test
    void collectionPreservesCourseRightsCommissionAndTierWithoutCreatingVisits() {
        String before = courseSnapshot();
        var holders = entitlements();
        var ledger = db.queryForList("select * from course_ledger order by ledger_id");
        Long seller = id("select seller_staff_id from course where course_id=?", courseId);
        String month = db.queryForObject("select sale_month from course where course_id=?", String.class, courseId);
        BigDecimal tier = bean(MonthlyTierSalesService.class).netSales(seller, month, branchId);
        Long sales = id("select count(*) from sales_transaction");
        Long courses = id("select count(*) from course");
        Long visits = id("select count(*) from patient_visit");
        Long appointments = id("select count(*) from appointment");

        var result = collect("1000", Discount.of("BAHT", money("100")), "DEPOSIT", "900");
        assertMoney("900", result.netAmount());
        assertEquals(before, courseSnapshot());
        assertEquals(holders, entitlements());
        assertEquals(ledger, db.queryForList("select * from course_ledger order by ledger_id"));
        assertMoney(tier.toPlainString(), bean(MonthlyTierSalesService.class).netSales(seller, month, branchId));
        assertEquals(sales, id("select count(*) from sales_transaction"));
        assertEquals(courses, id("select count(*) from course"));
        assertEquals(visits, id("select count(*) from patient_visit"));
        assertEquals(appointments, id("select count(*) from appointment"));
        assertMoney("0", bean(SalesTransactionRepository.class).netSalesOfReceipt(result.receiptId()));
        assertMoney("900", bean(PaymentTransactionRepository.class).paidAmountOf(result.receiptId()));
        assertEquals(3L, id("select count(*) from audit_log"));
    }

    @ParameterizedTest
    @ValueSource(strings={"DEPOSIT","INSTALLMENT","FULL"})
    void labelsDoNotDetermineManualCollectionAmount(String label) {
        var result = collect("17.25", Discount.none(), label, "17.25");
        var detail = bean(ReceiptReadService.class).getDetail(result.receiptId());
        assertEquals(label, detail.paymentLabelCode());
        assertMoney("17.25", detail.netAmount());
        assertEquals("Completed", detail.payments().get(0).status());
        assertTrue(detail.sales().isEmpty());
        assertEquals(1, detail.coursePayments().size());
    }

    @Test
    void fullyDiscountedCollectionRecordsZeroCompletedPayment() {
        var result = collect("100", Discount.of("BAHT", money("100")), "INSTALLMENT", "0");
        var detail = bean(ReceiptReadService.class).getDetail(result.receiptId());
        assertMoney("0", detail.netAmount());
        assertMoney("100", detail.coursePayments().get(0).allocatedBillDiscountAmount());
        assertMoney("0", detail.payments().get(0).amount());
        assertEquals("Completed", detail.payments().get(0).status());
    }

    @ParameterizedTest
    @ValueSource(strings={"-0.01","0.001","10000000000.00"})
    void invalidItemAmountsRollBackTheWholeReceipt(String value) {
        var before = counts();
        assertThrows(IllegalArgumentException.class,
                () -> collect(value, Discount.none(), "FULL", "0"));
        assertEquals(before, counts());
    }

    @Test
    void paymentMismatchRollsBackReceiptItemsPaymentAndAudit() {
        var before = counts();
        String original = courseSnapshot();
        assertThrows(IllegalArgumentException.class,
                () -> collect("100", Discount.none(), "FULL", "99"));
        assertEquals(before, counts());
        assertEquals(original, courseSnapshot());
    }

    @Test
    void duplicateCourseAndWrongBuyerAreRejectedAtomically() {
        var before = counts();
        assertThrows(IllegalArgumentException.class, () -> collect(
                patientId, branchId, List.of(one("10").get(0), one("10").get(0)),
                Discount.none(), "FULL", "20"));
        Long other = id("select patient_id from patient where patient_id<>? order by patient_id limit 1", patientId);
        assertThrows(IllegalArgumentException.class,
                () -> collect(other, branchId, one("10"), Discount.none(), "FULL", "10"));
        assertEquals(before, counts());
    }

    @Test
    void disabledPaymentMethodOrLabelRejectsCollectionWithoutPartialData() {
        var before = counts();
        db.update("update payment_label set payment_label_status=false where payment_label_code='DEPOSIT'");
        assertThrows(IllegalArgumentException.class,
                () -> collect("10", Discount.none(), "DEPOSIT", "10"));
        db.update("update payment_method set payment_method_status=false where payment_method_id=?", methodId);
        assertThrows(IllegalArgumentException.class,
                () -> collect("10", Discount.none(), "FULL", "10"));
        assertEquals(before, counts());
    }

    @Test
    void collectionRetainsPreviouslyLockedCourseValues() {
        db.update("update course set commission_status='Locked',locked_commission_rate=0.1,"
                + "total_course_commission_pool=1000,commission_allocation_per_visit=100,"
                + "commission_generated_date=date '2026-01-31' where course_id=?", courseId);
        String before = courseSnapshot();
        collect("10", Discount.none(), "FULL", "10");
        assertEquals(before, courseSnapshot());
    }

    @Test
    void branchAccessIsCheckedAndCollectionDoesNotChangeOriginBranch() {
        Long otherBranch = id("select branch_id from branch where branch_code='02'");
        String before = courseSnapshot();
        var result = collect(patientId, otherBranch, one("10"), Discount.none(), "FULL", "10");
        assertEquals(otherBranch, bean(ReceiptReadService.class).getDetail(result.receiptId()).branchId());
        assertEquals(before, courseSnapshot());

        authenticate("therapist2");
        var savedCounts = counts();
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> collect("10", Discount.none(), "FULL", "10"));
        assertEquals(403, forbidden.getStatusCode().value());
        assertEquals(savedCounts, counts());
    }

    @Test
    void historyIncludesOriginalAndEveryCollectionOnceAndPreservesSnapshot() {
        String originalName = db.queryForObject("select course_name from course where course_id=?", String.class, courseId);
        Long originalReceipt = id("select receipt_id from course where course_id=?", courseId);
        var first = collect("10", Discount.none(), "DEPOSIT", "10");
        var second = collect("20", Discount.none(), "INSTALLMENT", "20");
        db.update("update course set course_name='Renamed after collection' where course_id=?", courseId);
        db.update("update payment_label set payment_label_status=false where payment_label_code='DEPOSIT'");
        var read = bean(ReceiptReadService.class);
        assertEquals(originalName, read.getDetail(first.receiptId()).coursePayments().get(0).itemNameSnapshot());
        var history = read.getCourseHistory(courseId);
        assertEquals(4, history.size());
        assertEquals(originalReceipt, history.get(0).receiptId());
        assertEquals(4, history.stream().map(ReceiptReadService.Detail::receiptId).distinct().count());
        assertTrue(history.stream().anyMatch(receipt -> receipt.receiptId().equals(second.receiptId())));
        var page = read.searchReceiptHistory(patientId, null, 0, 1);
        assertEquals(4, page.getTotalElements());
        assertEquals(1, page.getContent().size());
    }

    @Test
    void voidChangesOnlyItsReceiptPaymentAndAuditedCancellation() {
        var result = collect("10", Discount.none(), "FULL", "10");
        String before = courseSnapshot();
        var holders = entitlements();
        var ledger = db.queryForList("select * from course_ledger order by ledger_id");
        var voided = bean(CoursePaymentVoidService.class)
                .voidReceipt(result.receiptId(), ReasonCode.PatientRequested, "Patient requested cancellation");
        var detail = bean(ReceiptReadService.class).getDetail(result.receiptId());
        assertEquals("Voided", detail.receiptStatus());
        assertEquals("Voided", detail.payments().get(0).status());
        assertEquals(result.paymentId(), voided.paymentId());
        assertEquals(before, courseSnapshot());
        assertEquals(holders, entitlements());
        assertEquals(ledger, db.queryForList("select * from course_ledger order by ledger_id"));
        var cancellation = db.queryForMap("select payment_id,reason_code::text,reason,cancelled_by,cancelled_at "
                + "from transaction_cancellation where cancellation_id=?", voided.cancellationId());
        assertEquals(result.paymentId(), cancellation.get("payment_id"));
        assertEquals("PatientRequested", cancellation.get("reason_code"));
        assertEquals(actorId, cancellation.get("cancelled_by"));
        assertNotNull(cancellation.get("cancelled_at"));
        assertEquals(6L, id("select count(*) from audit_log"));
        assertThrows(IllegalArgumentException.class, () -> bean(CoursePaymentVoidService.class)
                .voidReceipt(result.receiptId(), ReasonCode.Other, "Repeated cancellation"));
        assertMoney("0", bean(PaymentTransactionRepository.class).paidAmountOf(result.receiptId()));
        assertTrue(bean(ReceiptReadService.class).getCourseHistory(courseId).stream()
                .anyMatch(receipt -> receipt.receiptId().equals(result.receiptId())
                        && receipt.receiptStatus().equals("Voided")));
    }

    @Test
    void voidRejectsMissingReasonOriginalPurchaseAndUnauthorizedBranch() {
        var result = collect("10", Discount.none(), "FULL", "10");
        var service = bean(CoursePaymentVoidService.class);
        assertThrows(IllegalArgumentException.class, () -> service.voidReceipt(result.receiptId(), null, "Reason"));
        assertThrows(IllegalArgumentException.class, () -> service.voidReceipt(result.receiptId(), ReasonCode.Other, " "));
        Long original = id("select receipt_id from course where course_id=?", courseId);
        assertThrows(IllegalArgumentException.class, () -> service.voidReceipt(original, ReasonCode.Other, "Reason"));
        authenticate("therapist2");
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> service.voidReceipt(result.receiptId(), ReasonCode.Other, "Reason"));
        assertEquals(403, forbidden.getStatusCode().value());
    }

    private Long addSecondCourse() {
        Long originalReceipt = id("select receipt_id from course where course_id=?", courseId);
        Long originalSale = id("select sale_transaction_id from course where course_id=?", courseId);
        Long secondSale = copy("sales_transaction", "transaction_id", originalSale,
                Map.of("line_no", "(select max(line_no)+1 from sales_transaction where receipt_id=" + originalReceipt + ")"));
        Long sequence = id("select max(course_sequence)+1 from course where receipt_id=?", originalReceipt);
        Long second = copy("course", "course_id", courseId, Map.of(
                "sale_transaction_id", secondSale.toString(), "course_sequence", sequence.toString(),
                "course_no", "'RE99110199-" + String.format("%02d", sequence) + "'"));
        db.update("insert into course_shared_member select ?,patient_id,member_role,member_status,joined_at,ended_at "
                + "from course_shared_member where course_id=?", second, courseId);
        db.update("insert into course_entitlement select ?,patient_id,allocated_visits,used_visits,remaining_visits,updated_at "
                + "from course_entitlement where course_id=?", second, courseId);
        Long purchaseLedger = id("select ledger_id from course_ledger where course_id=? and entry_type='Purchase'", courseId);
        copy("course_ledger", "ledger_id", purchaseLedger,
                Map.of("course_id", second.toString(), "sales_transaction_id", secondSale.toString()));
        db.update("update receipt set subtotal_before_discount=subtotal_before_discount+10000,"
                + "net_amount=net_amount+10000,taxable_amount=taxable_amount+10000 where receipt_id=?", originalReceipt);
        db.update("update payment_transaction set amount=amount+10000 where receipt_id=?", originalReceipt);
        return second;
    }
    private static Long copy(String table, String key, Long source, Map<String,String> overrides) {
        List<String> columns = db.queryForList("select column_name from information_schema.columns "
                + "where table_schema='public' and table_name=? and column_name<>? and is_generated='NEVER' "
                + "order by ordinal_position", String.class, table, key);
        String values = String.join(",", columns.stream()
                .map(column -> overrides.getOrDefault(column, "s." + column)).toList());
        return id("insert into " + table + "(" + String.join(",", columns) + ") select " + values
                + " from " + table + " s where s." + key + "=? returning " + key, source);
    }

    @Test
    void oneReceiptCollectsMultipleCoursesAndAllocatesResidualCentsOnce() {
        Long second = addSecondCourse();
        var result = collect(patientId, branchId, List.of(
                new ReceiptCoursePaymentService.ItemRequest(second, money("1")),
                new ReceiptCoursePaymentService.ItemRequest(courseId, money("1"))),
                Discount.of("BAHT", money("0.01")), "FULL", "1.99");
        var detail = bean(ReceiptReadService.class).getDetail(result.receiptId());
        assertEquals(2, detail.coursePayments().size());
        assertMoney("0.01", detail.coursePayments().stream()
                .map(ReceiptReadService.CoursePaymentLine::allocatedBillDiscountAmount).reduce(ZERO, BigDecimal::add));
        assertMoney("1.99", detail.netAmount());
        assertEquals(courseId, detail.coursePayments().get(0).courseId());
        assertTrue(detail.coursePayments().stream().allMatch(item ->
                item.allocatedBillDiscountAmount().signum() >= 0
                        && item.allocatedBillDiscountAmount().compareTo(item.amount()) <= 0));
        assertEquals(10, db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId));
        assertEquals(10, db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, second));
    }

    @Test
    void mixedReceiptTotalsPreserveCommissionBaseAndApplyDiscountOnlyOnce() {
        String original = courseSnapshot();
        Long receiptId = transaction(() -> {
            var receiptService = bean(ReceiptService.class);
            Patient patient = em().find(Patient.class, patientId);
            Branch branch = em().find(Branch.class, branchId);
            var receipt = receiptService.create(receiptService.nextReceiptNo(branch, ClinicTime.today()),
                    patient, branch, ClinicTime.today(), "FULL", actorId);
            Service template = em().find(Service.class, id("select service_id from service where service_code='COURSE_10'"));
            Staff seller = em().find(Staff.class, id("select staff_id from staff where staff_code='THP-001'"));
            var purchase = bean(SalesTransactionService.class).recordCoursePurchase(
                    receipt, patient, branch, template, seller, money("1"), 10, 0,
                    Discount.none(), ClinicTime.today(), actorId);
            em().flush();
            var prepared = bean(ReceiptCoursePaymentService.class).prepareAll(receipt, one("1"));
            receiptService.finalizeTotals(receipt, List.of(purchase.transaction()), prepared,
                    Discount.of("BAHT", money("0.01")), actorId);
            bean(ReceiptCoursePaymentRepository.class).saveAllAndFlush(prepared);
            bean(PaymentTransactionService.class).record(patient, branch, receipt,
                    money("1.99"), em().find(PaymentMethod.class, methodId), actorId);
            return receipt.getId();
        });
        var detail = bean(ReceiptReadService.class).getDetail(receiptId);
        assertMoney("2", detail.subtotalBeforeDiscount());
        assertMoney("0.01", detail.billDiscountAmount());
        assertMoney("1.99", detail.netAmount());
        assertMoney("0.01", detail.sales().get(0).allocatedBillDiscountAmount()
                .add(detail.coursePayments().get(0).allocatedBillDiscountAmount()));
        assertMoney("1", db.queryForObject("select commissionable_amount from sales_transaction where receipt_id=?",
                BigDecimal.class, receiptId));
        assertMoney("0.99", db.queryForObject("select treatment_fee_base_amount from course where receipt_id=?",
                BigDecimal.class, receiptId));
        assertEquals(original, courseSnapshot());
    }

    @Test
    void lineAndBillDiscountCannotBeCombinedAndWritesRollBack() {
        var before = counts();
        assertThrows(IllegalArgumentException.class, () -> transaction(() -> {
            Patient patient = em().find(Patient.class, patientId);
            Branch branch = em().find(Branch.class, branchId);
            ReceiptService receipts = bean(ReceiptService.class);
            Receipt receipt = receipts.create(receipts.nextReceiptNo(branch, ClinicTime.today()),
                    patient, branch, ClinicTime.today(), "FULL", actorId);
            var purchase = bean(SalesTransactionService.class).recordCoursePurchase(
                    receipt, patient, branch,
                    em().find(Service.class, id("select service_id from service where service_code='COURSE_10'")),
                    em().find(Staff.class, id("select staff_id from staff where staff_code='THP-001'")),
                    money("100"), 10, 0, Discount.of("BAHT", money("10")), ClinicTime.today(), actorId);
            em().flush();
            var prepared = bean(ReceiptCoursePaymentService.class).prepareAll(receipt, one("50"));
            return receipts.finalizeTotals(receipt, List.of(purchase.transaction()), prepared,
                    Discount.of("BAHT", money("1")), actorId);
        }));
        assertEquals(before, counts());
    }

    @Test
    void paymentQueryUsesInclusiveStartExclusiveEndAndBranchAndStatusFilters() {
        Long originalPayment = id("select p.payment_id from payment_transaction p join course c using(receipt_id) where c.course_id=?", courseId);
        Long fixtureCollection = id("select p.payment_id from payment_transaction p join receipt_course_payment i using(receipt_id) where i.course_id=?", courseId);
        var lastInside = collect("1", Discount.none(), "FULL", "1");
        var beforeStart = collect("1", Discount.none(), "FULL", "1");
        var afterEnd = collect("1", Discount.none(), "FULL", "1");
        Long secondBranch = id("select branch_id from branch where branch_code='02'");
        var otherBranch = collect(patientId, secondBranch, one("1"), Discount.none(), "FULL", "1");
        Instant from = Instant.parse("2026-01-01T17:00:00Z");
        Instant to = Instant.parse("2026-01-02T17:00:00Z");
        for (var entry : Map.of(
                originalPayment, from, fixtureCollection, to, lastInside.paymentId(), to.minusMillis(1),
                beforeStart.paymentId(), from.minusMillis(1), afterEnd.paymentId(), to.plusMillis(1),
                otherBranch.paymentId(), from).entrySet()) {
            db.update("update payment_transaction set payment_date=? where payment_id=?",
                    java.sql.Timestamp.from(entry.getValue()), entry.getKey());
        }
        var repo = bean(PaymentTransactionRepository.class);
        assertEquals(Set.of(originalPayment, lastInside.paymentId()),
                new HashSet<>(repo.findByPaymentDateBetween(from, to, branchId).stream().map(PaymentTransaction::getId).toList()));
        assertEquals(Set.of(originalPayment, lastInside.paymentId(), otherBranch.paymentId()),
                new HashSet<>(repo.findByPaymentDateBetween(from, to, null).stream().map(PaymentTransaction::getId).toList()));
        db.update("update payment_transaction set payment_status=false where payment_id=?", lastInside.paymentId());
        assertEquals(List.of(originalPayment),
                repo.findByPaymentDateBetween(from, to, branchId).stream().map(PaymentTransaction::getId).toList());
    }
}