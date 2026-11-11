package com.sabyeguyz.schemacheck;

import com.sabyeguyz.clinicv2.ClinicV2Application;
import com.sabyeguyz.clinicv2.course.*;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.sale.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.SpringApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Run with mvn test and Docker. PostgreSQL, migrations and fixture data are isolated
 * from local databases; every test gets a fresh transactional fixture.
 */
@Testcontainers
@Timeout(90)
class SharedCourseRefundTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
    private static boolean legacyPreserved;
    private static ConfigurableApplicationContext context;
    private static JdbcTemplate db;
    private static TransactionTemplate tx;
    private static RefundService refunds;
    private static CourseRightsService rights;
    private static CourseRefundItemRepository items;
    private static PaymentTransactionRepository payments;
    private static Long courseId, actorId, permissionId, paymentA, paymentB;

    private record Group(Long mainRefund, Long relatedRefund, Long mainItem, Long relatedItem) {}

    private static BigDecimal amount(String value) { return new BigDecimal(value); }
    private static void check(boolean condition, String label) {
        assertTrue(condition, label);
    }
    private static EntityManager em() {
        return EntityManagerFactoryUtils.getTransactionalEntityManager(
                context.getBean(EntityManagerFactory.class));
    }
    private static RefundTransaction request(Long paymentId, List<RefundService.ItemRequest> requested) {
        PaymentTransaction payment = payments.findById(paymentId).orElseThrow();
        BigDecimal total = requested.stream().map(RefundService.ItemRequest::refundAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return refunds.request(payment, total, payment.getPaymentMethod(),
                em().find(Permission.class, permissionId), "Shared refund integration check", actorId, requested);
    }
    private static CourseRefundItem main(Long selectedCourse, Long payment, int visits, String money) {
        RefundTransaction refund = request(payment, List.of(
                new RefundService.ItemRequest(selectedCourse, amount(money), visits)));
        em().flush();
        return items.findByRefundIdOrderByIdAsc(refund.getId()).get(0);
    }
    private static Group group() {
        CourseRefundItem main = main(courseId, paymentA, 2, "20");
        RefundTransaction related = request(paymentB, List.of(
                new RefundService.ItemRequest(courseId, amount("20"), null, main.getId())));
        em().flush();
        Long relatedId = items.findByRefundIdOrderByIdAsc(related.getId()).get(0).getId();
        return new Group(main.getRefund().getId(), related.getId(), main.getId(), relatedId);
    }
    private static int remaining(Long id) {
        return db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, id);
    }
    private static long ledgerRows(Long mainId, String type) {
        em().flush();
        return db.queryForObject("select count(*) from course_ledger where course_refund_item_id=? "
                + "and entry_type::text=?", Long.class, mainId, type);
    }
    private static void rollback(Runnable body) {
        tx.executeWithoutResult(status -> { status.setRollbackOnly(); body.run(); });
    }
    private static void rejected(String expected, Runnable body) {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> rollback(body));
        assertTrue(failure.getMessage().contains(expected), failure.getMessage());
    }
    private static void sequential() {
        for (boolean relatedFirst : List.of(false, true)) rollback(() -> {
            int before = remaining(courseId);
            Group g = group();
            refunds.complete(relatedFirst ? g.relatedRefund() : g.mainRefund(), actorId);
            refunds.complete(relatedFirst ? g.mainRefund() : g.relatedRefund(), actorId);
            em().flush();
            check(remaining(courseId) == before - 2, "approval order cuts once: " + relatedFirst);
            rights.cutForRefund(items.findById(g.relatedItem()).orElseThrow(), actorId);
            em().flush();
            check(remaining(courseId) == before - 2, "repeated rights cut is unchanged");
            check(ledgerRows(g.relatedItem(), "Cancellation") == 0, "ledger references main only");
        });
        rollback(() -> {
            int before = remaining(courseId);
            Group g = group();
            refunds.fail(g.relatedRefund()); em().flush();
            refunds.retry(g.relatedRefund()); em().flush();
            check(items.findByRefundIdOrderByIdAsc(g.relatedRefund()).get(0)
                    .getRightsCancellationItem().getId().equals(g.mainItem()), "retry retains original group");
            refunds.complete(g.relatedRefund(), actorId);
            refunds.voidRequest(g.mainRefund(), actorId);
            em().flush();
            check(remaining(courseId) == before - 2, "completed related prevents rights restoration");
            check(ledgerRows(g.mainItem(), "Reversal") == 0, "no reversal when group has completed refund");
        });
        rollback(() -> {
            int before = remaining(courseId);
            Group g = group();
            rights.cutForRefund(items.findById(g.relatedItem()).orElseThrow(), actorId);
            refunds.fail(g.relatedRefund()); em().flush();
            refunds.voidRequest(g.mainRefund(), actorId);
            em().flush();
            check(remaining(courseId) == before - 2, "failed related prevents early restoration");
            refunds.voidRequest(g.relatedRefund(), actorId);
            em().flush();
            check(remaining(courseId) == before, "last void restores actual cancellation");
            long reversalCount = ledgerRows(g.mainItem(), "Reversal");
            rights.restoreForVoidedGroup(items.findById(g.mainItem()).orElseThrow(), actorId);
            check(ledgerRows(g.mainItem(), "Reversal") == reversalCount, "restoration is idempotent");
        });
        rollback(() -> {
            int before = remaining(courseId);
            Group g = group();
            refunds.voidRequest(g.mainRefund(), actorId);
            refunds.voidRequest(g.relatedRefund(), actorId);
            check(ledgerRows(g.mainItem(), "Reversal") == 0, "uncut voided group creates no reversal");
            check(remaining(courseId) == before, "uncut group adds no rights");
        });
        rollback(() -> {
            int before = remaining(courseId);
            Group g = group();
            refunds.complete(g.mainRefund(), actorId);
            refunds.fail(g.relatedRefund()); em().flush();
            refunds.retry(g.relatedRefund()); em().flush();
            refunds.complete(g.relatedRefund(), actorId);
            em().flush();
            check(remaining(courseId) == before - 2, "retry after group cut does not cut again");
        });
    }
    private static void closedCourseRestoration() {
        rollback(() -> {
            db.update("update refund_transaction set refund_status='Voided' where refund_status='Pending' "
                    + "and refund_id in (select refund_id from course_refund_item where course_id=?)", courseId);
            em().clear();
            int before = remaining(courseId);
            CourseRefundItem full = main(courseId, paymentA, before, "20");
            rights.cutForRefund(full, actorId);
            em().flush();
            check("Refunded".equals(db.queryForObject("select course_status::text from course where course_id=?",
                    String.class, courseId)), "full cancellation closes course as Refunded");
            refunds.voidRequest(full.getRefund().getId(), actorId);
            em().flush();
            check(remaining(courseId) == before, "full group reversal restores all remaining rights");
            check("Active".equals(db.queryForObject("select course_status::text from course where course_id=?",
                    String.class, courseId)), "restored usable rights reopen course as Active");
            CourseRefundItem next = main(courseId, paymentA, 1, "10");
            check(next.getRightsCancellationItem() == null && !next.getId().equals(full.getId()),
                    "a later cancellation creates a new independent main item");
        });
    }
    private static void validation() {
        rejected("รายการหลัก", () -> request(paymentA, List.of(
                new RefundService.ItemRequest(courseId, amount("1"), null, null))));
        rejected("รายการร่วมต้องไม่ระบุ", () -> {
            Group g = group();
            request(paymentB, List.of(new RefundService.ItemRequest(courseId, amount("1"), 1, g.mainItem())));
        });
        rejected("ไม่พบรายการหลัก", () -> {
            Group g = group();
            request(paymentB, List.of(new RefundService.ItemRequest(courseId, amount("1"), null, g.relatedItem())));
        });
        rejected("กลับสิทธิ์แล้ว", () -> {
            Group g = group();
            rights.cutForRefund(items.findById(g.mainItem()).orElseThrow(), actorId);
            refunds.voidRequest(g.mainRefund(), actorId);
            refunds.voidRequest(g.relatedRefund(), actorId);
            request(paymentB, List.of(new RefundService.ItemRequest(courseId, amount("1"), null, g.mainItem())));
        });
        rejected("กลับสิทธิ์แล้ว", () -> {
            Group g = group();
            rights.cutForRefund(items.findById(g.mainItem()).orElseThrow(), actorId);
            refunds.voidRequest(g.mainRefund(), actorId);
            refunds.voidRequest(g.relatedRefund(), actorId);
            rights.cutForRefund(items.findById(g.mainItem()).orElseThrow(), actorId);
        });
        rejected("ลองใหม่ได้เฉพาะ", () -> refunds.retry(group().mainRefund()));
        rejected("ยกเลิกได้เฉพาะ", () -> {
            Group g = group(); refunds.complete(g.mainRefund(), actorId); em().flush();
            refunds.voidRequest(g.mainRefund(), actorId);
        });
        rejected("ต้องไม่เกินยอดที่ชำระ", () -> {
            Group g = group(); refunds.fail(g.relatedRefund()); em().flush();
            db.update("update payment_transaction set amount=1 where payment_id=?", paymentB);
            em().clear(); refunds.retry(g.relatedRefund()); em().flush();
        });
        rejected("เกินยอดรับสุทธิ", () -> {
            Group g = group(); refunds.fail(g.relatedRefund()); em().flush();
            db.update("update receipt_course_payment set amount=allocated_bill_discount_amount+1 "
                    + "where course_id=? and receipt_id=(select receipt_id from payment_transaction where payment_id=?)",
                    courseId, paymentB);
            em().clear(); refunds.retry(g.relatedRefund()); em().flush();
        });
        rejected("สิทธิ์คงเหลือ", () -> {
            Group g = group(); refunds.fail(g.mainRefund()); em().flush(); refunds.fail(g.relatedRefund()); em().flush();
            db.update("update course set remaining_visits=1 where course_id=?", courseId);
            em().clear(); refunds.retry(g.mainRefund()); em().flush();
        });
        rejected("สิทธิ์คงเหลือ", () -> {
            Group g = group();
            db.update("update course set remaining_visits=1 where course_id=?", courseId);
            em().clear(); refunds.complete(g.mainRefund(), actorId);
        });
    }
    /** Copies fixture data without assuming the table's non-key column list. */
    private static Long copy(String table, String key, Long source, Map<String, String> overrides) {
        List<String> columns = db.queryForList("select column_name from information_schema.columns "
                + "where table_schema='public' and table_name=? and column_name<>? and is_generated='NEVER' "
                + "order by ordinal_position", String.class, table, key);
        String expressions = String.join(",", columns.stream()
                .map(column -> overrides.getOrDefault(column, "s." + column)).toList());
        return db.queryForObject("insert into " + table + "(" + String.join(",", columns) + ") select "
                + expressions + " from " + table + " s where s." + key + "=? returning " + key, Long.class, source);
    }
    private static Long secondCourse() {
        Long sale = db.queryForObject("select sale_transaction_id from course where course_id=?", Long.class, courseId);
        Long receipt = db.queryForObject("select receipt_id from course where course_id=?", Long.class, courseId);
        Long newSale = copy("sales_transaction", "transaction_id", sale, Map.of("line_no",
                "(select max(line_no)+1 from sales_transaction where receipt_id=" + receipt + ")"));
        Long next = db.queryForObject("select max(course_sequence)+1 from course where receipt_id=?", Long.class, receipt);
        Long second = copy("course", "course_id", courseId, Map.of(
                "sale_transaction_id", newSale.toString(), "course_sequence", next.toString(),
                "course_no", "'RE99110199-" + String.format("%02d", next) + "'"));
        db.update("insert into course_shared_member select ?,patient_id,member_role,member_status,joined_at,ended_at "
                + "from course_shared_member where course_id=?", second, courseId);
        db.update("insert into course_entitlement select ?,patient_id,allocated_visits,used_visits,remaining_visits,updated_at "
                + "from course_entitlement where course_id=?", second, courseId);
        Long originalLedger = db.queryForObject("select ledger_id from course_ledger where course_id=? "
                + "and entry_type='Purchase' order by ledger_id limit 1", Long.class, courseId);
        copy("course_ledger", "ledger_id", originalLedger,
                Map.of("course_id", second.toString(), "sales_transaction_id", newSale.toString()));
        Long collectionReceipt = db.queryForObject("select receipt_id from payment_transaction where payment_id=?", Long.class, paymentB);
        db.update("insert into receipt_course_payment values (?,?,?,1000,100)", collectionReceipt, second, "Second fixture course");
        db.update("update receipt set subtotal_before_discount=subtotal_before_discount+1000,"
                + "bill_discount_amount=bill_discount_amount+100,net_amount=net_amount+900,"
                + "taxable_amount=taxable_amount+900 where receipt_id=?", collectionReceipt);
        db.update("update payment_transaction set amount=amount+900 where payment_id=?", paymentB);
        return second;
    }
    private static void multipleCourses() {
        rollback(() -> {
            int before = remaining(courseId);
            Long second = secondCourse();
            RefundTransaction main = request(paymentA, List.of(
                    new RefundService.ItemRequest(second, amount("20"), 1),
                    new RefundService.ItemRequest(courseId, amount("20"), 1)));
            em().flush();
            var roots = items.findByRefundIdOrderByIdAsc(main.getId());
            CourseRefundItem firstRoot = roots.stream().filter(i -> i.getCourse().getId().equals(courseId)).findFirst().orElseThrow();
            CourseRefundItem secondRoot = roots.stream().filter(i -> i.getCourse().getId().equals(second)).findFirst().orElseThrow();
            RefundTransaction related = request(paymentB, List.of(
                    new RefundService.ItemRequest(courseId, amount("20"), null, firstRoot.getId())));
            em().flush();
            rights.cutForRefund(firstRoot, actorId); rights.cutForRefund(secondRoot, actorId);
            refunds.complete(related.getId(), actorId);
            refunds.voidRequest(main.getId(), actorId);
            em().flush();
            check(remaining(courseId) == before - 1, "multi-course completed group retains cancellation");
            check(remaining(second) == before, "multi-course fully voided group restores separately");
        });
        rejected("คอร์สเดียว", () -> {
            Long second = secondCourse();
            CourseRefundItem root = main(courseId, paymentA, 1, "20");
            request(paymentB, List.of(new RefundService.ItemRequest(second, amount("1"), null, root.getId())));
        });
    }
    private static void sharedHolders() {
        rollback(() -> {
            Long purchaser = db.queryForObject("select purchaser_patient_id from course where course_id=?", Long.class, courseId);
            Long other = db.queryForObject("select patient_id from patient where patient_id<>? order by patient_id limit 1",
                    Long.class, purchaser);
            String roleType = db.queryForObject("select udt_name from information_schema.columns where table_schema='public' "
                    + "and table_name='course_shared_member' and column_name='member_role'", String.class);
            String role = db.queryForObject("select e.enumlabel from pg_enum e join pg_type t on t.oid=e.enumtypid "
                    + "where t.typname=? and e.enumlabel<>(select member_role::text from course_shared_member "
                    + "where course_id=? and patient_id=?) order by e.enumsortorder limit 1",
                    String.class, roleType, courseId, purchaser);
            int total = remaining(courseId);
            db.update("insert into course_shared_member(course_id,patient_id,member_role) values(?,?,cast(? as " + roleType + "))",
                    courseId, other, role);
            db.update("update course_entitlement set allocated_visits=used_visits+1,remaining_visits=1 where course_id=? and patient_id=?",
                    courseId, purchaser);
            db.update("insert into course_entitlement values(?,?,?,0,?,CURRENT_TIMESTAMP)", courseId, other, total - 1, total - 1);
            for (Long patient : List.of(purchaser, other)) {
                int delta = patient.equals(purchaser) ? -(total - 1) : total - 1;
                int balance = patient.equals(purchaser) ? 1 : total - 1;
                db.update("insert into course_ledger(course_id,patient_id,entry_type,qty_change,balance_after,remark,"
                        + "ledger_create_at,ledger_create_by,event_at,adjustment_approved_by,adjustment_approved_at) "
                        + "values(?,?,'Adjustment',?,?,'Fixture rights distribution',CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)",
                        courseId, patient, delta, balance, actorId, actorId);
            }
            var before = db.queryForList("select patient_id,allocated_visits,used_visits,remaining_visits from course_entitlement "
                    + "where course_id=? order by patient_id", courseId);
            em().clear();
            Group g = group();
            rights.cutForRefund(items.findById(g.mainItem()).orElseThrow(), actorId);
            check(ledgerRows(g.mainItem(), "Cancellation") == 2, "shared cancellation cuts two holders");
            refunds.voidRequest(g.mainRefund(), actorId); refunds.voidRequest(g.relatedRefund(), actorId);
            em().flush();
            check(before.equals(db.queryForList("select patient_id,allocated_visits,used_visits,remaining_visits "
                    + "from course_entitlement where course_id=? order by patient_id", courseId)),
                    "reversal restores each original holder");
            check(ledgerRows(g.mainItem(), "Reversal") == 2, "shared reversal restores every original ledger row");
            check(remaining(courseId) == total, "shared course total restored");
        });
    }
    private static void action(String action, Long id) {
        switch (action) {
            case "complete" -> refunds.complete(id, actorId);
            case "fail" -> refunds.fail(id);
            case "void" -> refunds.voidRequest(id, actorId);
            case "retry" -> refunds.retry(id);
            case "related" -> request(paymentB, List.of(
                    new RefundService.ItemRequest(courseId, amount("1"), null, id)));
            default -> throw new IllegalArgumentException(action);
        }
    }
    private static void race(String firstAction, String secondAction) throws Exception {
        Group g = tx.execute(status -> group());
        if (secondAction.equals("retry")) tx.executeWithoutResult(status -> {
            refunds.fail(g.relatedRefund());
        });
        int before = remaining(courseId);
        CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1), entered = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        String label = firstAction + "/" + secondAction;
        try {
            Future<?> first = pool.submit(() -> tx.executeWithoutResult(status -> {
                action(firstAction, g.mainRefund()); em().flush(); held.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("Release timeout");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }));
            if (!held.await(15, TimeUnit.SECONDS)) { first.get(1, TimeUnit.SECONDS); throw new AssertionError("First action not ready"); }
            Future<?> second = pool.submit(() -> tx.executeWithoutResult(status -> {
                db.execute("SET LOCAL application_name='shared_course_refund_check'");
                entered.countDown();
                action(secondAction, secondAction.equals("related") ? g.mainItem() : g.relatedRefund());
            }));
            if (!entered.await(10, TimeUnit.SECONDS)) throw new AssertionError("Second action not ready");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            boolean waiting = false;
            while (System.nanoTime() < deadline) {
                Long count = db.queryForObject("select count(*) from pg_stat_activity where "
                        + "application_name='shared_course_refund_check' and wait_event_type='Lock'", Long.class);
                if (count > 0) { waiting = true; break; }
                if (second.isDone()) break;
                Thread.sleep(25);
            }
            check(waiting && !second.isDone(), "concurrent " + label + " waits for lock");
            release.countDown();
            first.get(30, TimeUnit.SECONDS); second.get(30, TimeUnit.SECONDS);
            check(remaining(courseId) == before - (firstAction.equals("complete") || secondAction.equals("complete") ? 2 : 0),
                    "concurrent " + label + " preserves rights");
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @BeforeAll
    static void startApplication() {
        String[] locations = {"classpath:db/migration", "classpath:db/seed_v2"};
        // The fixture needs V1023 receipt collections, before V1024 adds shared refund rights.
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(locations).outOfOrder(true).target("1023").load().migrate();
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate beforeUpgrade = new JdbcTemplate(source);
        loadFixture(source);
        var originalItems = beforeUpgrade.queryForList(
                "select course_refund_item_id,refund_id,course_id,refund_amount,cancelled_visits "
                        + "from course_refund_item order by course_refund_item_id");
        assertFalse(originalItems.isEmpty(), "legacy upgrade must exercise an existing refund item");
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations(locations).outOfOrder(true).load().migrate();
        legacyPreserved = originalItems.equals(beforeUpgrade.queryForList(
                "select course_refund_item_id,refund_id,course_id,refund_amount,cancelled_visits "
                        + "from course_refund_item order by course_refund_item_id"))
                && beforeUpgrade.queryForObject("select count(*) from course_refund_item "
                        + "where rights_cancellation_item_id is not null", Long.class) == 0;
        context = SpringApplication.run(ClinicV2Application.class,
                "--spring.profiles.active=v2", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.datasource.hikari.data-source-properties.options=-c lock_timeout=5000",
                "--spring.flyway.locations=classpath:db/migration,classpath:db/seed_v2",
                "--spring.jpa.hibernate.ddl-auto=validate",
                "--app.jwt.secret=shared-refund-test-only-secret-at-least-32-characters",
                "--app.calendar.enabled=false", "--logging.level.root=WARN");
        db = context.getBean(JdbcTemplate.class);
        tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        refunds = context.getBean(RefundService.class);
        rights = context.getBean(CourseRightsService.class);
        items = context.getBean(CourseRefundItemRepository.class);
        payments = context.getBean(PaymentTransactionRepository.class);
    }

    private static void loadFixture(javax.sql.DataSource source) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("fixtures/shared-course-refund.sql"));
        populator.setSeparator(
                org.springframework.jdbc.datasource.init.ScriptUtils.EOF_STATEMENT_SEPARATOR);
        populator.execute(source);
    }

    @BeforeEach
    void resetFixture() {
        db.execute("TRUNCATE TABLE receipt RESTART IDENTITY CASCADE");
        loadFixture(context.getBean(javax.sql.DataSource.class));
        courseId = db.queryForObject("select course_id from course order by course_id limit 1", Long.class);
        actorId = db.queryForObject("select user_id from app_user where username='manager'", Long.class);
        permissionId = db.queryForObject("select permission_id from permission where permission_name='SALE_CANCEL'", Long.class);
        paymentA = db.queryForObject("select p.payment_id from payment_transaction p join course c on c.receipt_id=p.receipt_id "
                + "where c.course_id=? and p.payment_status=true", Long.class, courseId);
        paymentB = db.queryForObject("select p.payment_id from payment_transaction p join receipt_course_payment i "
                + "on i.receipt_id=p.receipt_id where i.course_id=? and p.payment_status=true", Long.class, courseId);
    }

    @AfterAll
    static void stopApplication() {
        if (context != null) context.close();
    }

    @Test
    void flywayUpgradePreservesLegacyRefundItemsAndValidatesMappings() {
        assertTrue(legacyPreserved);
        assertEquals(1L, db.queryForObject("select count(*) from flyway_schema_history "
                + "where version='1022' and success", Long.class));
    }

    @Test
    void approvalsRetriesAndGroupVoidsPreserveRights() {
        int before = remaining(courseId);
        Long refundCount = db.queryForObject("select count(*) from refund_transaction", Long.class);
        sequential();
        assertEquals(before, remaining(courseId));
        assertEquals(refundCount, db.queryForObject("select count(*) from refund_transaction", Long.class));
    }

    @Test
    void invalidReferencesTransitionsAmountsAndUnavailableRightsAreRejected() {
        validation();
    }

    @Test
    void eachCourseGroupIsRestoredIndependently() {
        multipleCourses();
    }

    @Test
    void reversalsRestoreEveryOriginalHolder() {
        sharedHolders();
    }

    @Test
    void fullRestorationReopensCourseAndAllowsANewCancellationEvent() {
        closedCourseRestoration();
    }

    @ParameterizedTest(name = "{0} and {1} serialize through the cancellation group")
    @CsvSource({"complete,complete", "void,complete", "complete,retry", "void,related"})
    void concurrentTransitionsWaitForGroupLocks(String first, String second) throws Exception {
        race(first, second);
    }
}
