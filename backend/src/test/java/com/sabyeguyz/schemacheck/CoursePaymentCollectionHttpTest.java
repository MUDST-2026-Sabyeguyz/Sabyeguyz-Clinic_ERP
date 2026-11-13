package com.sabyeguyz.schemacheck;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoursePaymentCollectionHttpTest extends CoursePaymentHttpTestSupport {
    private Map<String, Object> request(String amount) {
        return new LinkedHashMap<>(Map.of("patientId", patientId, "branchCode", "01",
                "items", List.of(Map.of("courseId", courseId, "amount", new BigDecimal(amount))),
                "paymentLabelCode", "INSTALLMENT", "paymentMethodCode", "PromptPay",
                "paymentAmount", new BigDecimal(amount)));
    }
    private HttpResult collect(String key, Map<String, Object> payload) {
        return replyWithHeaders(token, "POST", "/api/course-payment-collections", payload, Map.of("Idempotency-Key", key));
    }
    private long count(String table) { return db.queryForObject("select count(*) from " + table, Long.class); }

    @Test
    void manualDiscountCreatesOnlyReceiptPaymentAndAuditAndReplaysExactly() {
        long courses = count("course"), sales = count("sales_transaction"), visits = count("patient_visit"), ledger = count("course_ledger");
        long receipts = count("receipt"), payments = count("payment_transaction");
        var body = request("10000");
        body.put("billDiscountType", "FIXED"); body.put("billDiscountValue", new BigDecimal("3000"));
        body.put("paymentAmount", new BigDecimal("7000"));
        var first = collect("manual-collection-1", body);
        assertEquals(201, first.status(), first.body().toString());
        assertEquals(0, new BigDecimal("7000").compareTo(first.body().path("netAmount").decimalValue()));
        var replay = collect("manual-collection-1", body);
        assertEquals(201, replay.status()); assertEquals(first.body(), replay.body());
        assertEquals(receipts + 1, count("receipt")); assertEquals(payments + 1, count("payment_transaction"));
        assertEquals(courses, count("course")); assertEquals(sales, count("sales_transaction"));
        assertEquals(visits, count("patient_visit")); assertEquals(ledger, count("course_ledger"));
        assertEquals(1L, db.queryForObject("select count(*) from audit_log where table_name='course_payment_collection_request'", Long.class));
    }

    @Test
    void replaySurvivesApplicationRestartAndNewIdentityAllowsSameAmount() {
        var body = request("15"); var first = collect("restart-request", body);
        assertEquals(201, first.status(), first.body().toString());
        restartApplication(); token = login("manager", "manager123");
        assertEquals(first.body(), collect("restart-request", body).body());
        var second = collect("legitimate-second-request", body);
        assertEquals(201, second.status(), second.body().toString());
        assertNotEquals(first.body().path("receiptId"), second.body().path("receiptId"));
    }

    @Test
    void concurrentSameRequestHasOneCommittedReceiptAndOneReplay() throws Exception {
        long before = count("receipt"); var payload = request("25");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try (var held = context.getBean(javax.sql.DataSource.class).getConnection()) {
            held.setAutoCommit(false);
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest((actorId + "\nconcurrent-request").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try (var lock = held.prepareStatement("select pg_advisory_xact_lock(?)")) {
                lock.setLong(1, java.nio.ByteBuffer.wrap(digest).getLong()); lock.execute();
            }
            Callable<HttpResult> call = () -> { ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS)); return collect("concurrent-request", payload); };
            var first = pool.submit(call); var second = pool.submit(call);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            int waiting;
            do {
                waiting = db.queryForObject("select count(*) from pg_stat_activity where wait_event_type='Lock' and lower(wait_event) like '%advisory%'", Integer.class);
                if (waiting < 2) Thread.sleep(20);
            } while (waiting < 2 && System.nanoTime() < deadline);
            assertEquals(2, waiting, "Both HTTP requests must demonstrably wait on the database request lock");
            held.commit();
            var a = first.get(45, TimeUnit.SECONDS); var b = second.get(45, TimeUnit.SECONDS);
            assertEquals(201, a.status(), a.body().toString()); assertEquals(201, b.status(), b.body().toString());
            assertEquals(a.body(), b.body()); assertEquals(before + 1, count("receipt"));
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test
    void keyPayloadConflictIs409AndFailureDoesNotReserveKey() {
        var bad = request("10"); bad.put("paymentAmount", new BigDecimal("9"));
        long before = count("receipt");
        assertEquals(400, collect("rollback-request", bad).status()); assertEquals(before, count("receipt"));
        assertEquals(201, collect("rollback-request", request("10")).status());
        assertEquals(409, collect("rollback-request", request("11")).status());
        assertEquals(before + 1, count("receipt"));
    }

    @Test
    void zeroNetAndEquivalentMoneyNotationReplay() {
        var body = request("10"); body.put("billDiscountType", "BAHT"); body.put("billDiscountValue", new BigDecimal("10"));
        body.put("paymentAmount", BigDecimal.ZERO);
        var first = collect("zero-net-request", body); assertEquals(201, first.status(), first.body().toString());
        body.put("billDiscountType", "FIXED"); body.put("billDiscountValue", new BigDecimal("10.00"));
        assertEquals(first.body(), collect("zero-net-request", body).body());
    }

    @Test
    void missingKeyInvalidAmountsAndDisabledMethodsHaveNoPartialWrites() {
        long before = count("receipt");
        assertEquals(400, reply("POST", "/api/course-payment-collections", request("10")).status());
        assertEquals(400, collect("bad-precision", request("1.001")).status());
        assertEquals(400, collect("negative-amount", request("-1")).status());
        var preciseDiscount = request("100"); preciseDiscount.put("billDiscountType", "PERCENT");
        preciseDiscount.put("billDiscountValue", new BigDecimal("0.12345"));
        preciseDiscount.put("paymentAmount", new BigDecimal("99.88"));
        assertEquals(400, collect("over-precise-discount", preciseDiscount).status());
        var body = request("10"); body.put("items", Arrays.asList((Object) null));
        assertEquals(400, collect("null-item", body).status());
        body = request("10"); body.put("paymentMethodCode", "Cash");
        assertEquals(400, collect("cash-payment", body).status());
        db.update("update payment_method set payment_method_status=false where method_name='PromptPay'");
        assertEquals(400, collect("disabled-payment", request("10")).status());
        assertEquals(before, count("receipt"));
    }

    @Test
    void authorizationIsCheckedForOriginalAndReplay() {
        assertEquals(401, replyAs(null, "POST", "/api/course-payment-collections", request("10")).status());
        var body = request("10"); assertEquals(201, collect("branch-replay", body).status());
        db.update("update branch set branch_status=false where branch_code='01'");
        assertEquals(403, collect("branch-replay", body).status());
    }

    @Test
    void failureWritingReplayRecordRollsBackMoneyAndRetryCanSucceed() {
        long receipts = count("receipt"), payments = count("payment_transaction"), items = count("receipt_course_payment");
        db.execute("""
                create function test_fail_request_audit() returns trigger language plpgsql as $$
                begin
                  if NEW.table_name='course_payment_collection_request' then
                    raise exception 'Simulated failure before commit';
                  end if;
                  return NEW;
                end $$
                """);
        db.execute("create trigger test_fail_request_audit before insert on audit_log for each row execute function test_fail_request_audit()");
        try {
            var failed = collect("crash-before-commit", request("35"));
            assertTrue(failed.status() >= 400, failed.body().toString());
            assertEquals(receipts, count("receipt")); assertEquals(payments, count("payment_transaction"));
            assertEquals(items, count("receipt_course_payment"));
            assertEquals(0L, db.queryForObject("select count(*) from audit_log where table_name='course_payment_collection_request'", Long.class));
        } finally {
            db.execute("drop trigger test_fail_request_audit on audit_log");
            db.execute("drop function test_fail_request_audit()");
        }
        assertEquals(201, collect("crash-before-commit", request("35")).status());
        assertEquals(receipts + 1, count("receipt"));
    }

    @Test
    void duplicateCoursesAndWrongBuyerRollBackWithoutAReplayRecord() {
        long before = count("receipt");
        var duplicate = request("20");
        duplicate.put("items", List.of(Map.of("courseId", courseId, "amount", 10), Map.of("courseId", courseId, "amount", 10)));
        assertEquals(400, collect("duplicate-course", duplicate).status());
        Long otherPatient = db.queryForObject("select patient_id from patient where patient_id<>? order by patient_id limit 1", Long.class, patientId);
        var wrongBuyer = request("20"); wrongBuyer.put("patientId", otherPatient);
        assertEquals(400, collect("wrong-course-buyer", wrongBuyer).status());
        assertEquals(before, count("receipt"));
    }

    @Test
    void actorComesFromTokenAndKeysAreScopedToThatActor() {
        var payload = request("10"); payload.put("actorId", 999999);
        var manager = collect("per-actor-request", payload); assertEquals(201, manager.status());
        assertEquals(actorId, db.queryForObject("select receipt_create_by from receipt where receipt_id=?", Long.class,
                manager.body().path("receiptId").asLong()));
        token = login("therapist", "therapist123");
        var therapist = collect("per-actor-request", payload); assertEquals(201, therapist.status(), therapist.body().toString());
        assertNotEquals(manager.body().path("receiptId"), therapist.body().path("receiptId"));
        var otherBranch = request("10"); otherBranch.put("branchCode", "02");
        assertEquals(403, collect("therapist-other-branch", otherBranch).status());
    }

    @Test
    void receiptNumbersStaySeparatePerBranchMonthAndNeverReuseVoidedNumbersOrWrap() {
        var first = collect("number-before-void", request("10")); assertEquals(201, first.status());
        String firstNumber = first.body().path("receiptNo").asText();
        assertTrue(firstNumber.matches("RE\\d{8}"));
        assertEquals(200, reply("POST", "/api/receipt-documents/" + first.body().path("receiptId").asLong() + "/void",
                Map.of("reasonCode", "Other", "reason", "Number preservation check")).status());
        var next = collect("number-after-void", request("10")); assertEquals(201, next.status());
        assertNotEquals(firstNumber, next.body().path("receiptNo").asText());
        var receipts = context.getBean(com.sabyeguyz.clinicv2.sale.ReceiptService.class);
        var branches = context.getBean(com.sabyeguyz.clinicv2.branch.BranchRepository.class);
        var branch1 = branches.findByBranchCode("01").orElseThrow();
        var branch2 = branches.findByBranchCode("02").orElseThrow();
        db.update("delete from receipt_sequence where year_month in ('9812','9901')");
        var december = java.time.LocalDate.of(2098, 12, 1);
        assertEquals("RE98120001", receipts.nextReceiptNo(branch1, december));
        assertEquals("RE98120002", receipts.nextReceiptNo(branch1, december));
        assertEquals("RE98120001", receipts.nextReceiptNo(branch2, december));
        assertEquals("RE99010001", receipts.nextReceiptNo(branch1, december.plusMonths(1)));
        db.update("update receipt_sequence set last_running_no=9999 where branch_id=? and year_month='9812'", branchId);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> receipts.nextReceiptNo(branch1, december));
        assertEquals(9999, db.queryForObject("select last_running_no from receipt_sequence where branch_id=? and year_month='9812'", Integer.class, branchId));
    }

    @Test
    void multipleCoursesAllocateOneCentOnceAndItemOrderDoesNotChangeReplay() {
        Long template = db.queryForObject("select service_id from service where service_code='COURSE_10'", Long.class);
        var purchase = reply("POST", "/api/course-purchases", Map.of("patientId", patientId, "courseId", template,
                "branchCode", "01", "paymentLabelCode", "FULL"));
        assertEquals(201, purchase.status(), purchase.body().toString());
        long secondCourse = purchase.body().path("id").asLong();
        var body = request("1.99");
        var firstItem = Map.of("courseId", courseId, "amount", 1);
        var secondItem = Map.of("courseId", secondCourse, "amount", 1);
        body.put("items", List.of(secondItem, firstItem));
        body.put("billDiscountType", "BAHT"); body.put("billDiscountValue", new BigDecimal("0.01"));
        var collection = collect("multiple-course-receipt", body);
        assertEquals(201, collection.status(), collection.body().toString());
        var detail = reply("GET", "/api/receipt-documents/" + collection.body().path("receiptId").asLong(), null);
        assertEquals(200, detail.status()); assertEquals(0, detail.body().path("sales").size());
        assertEquals(2, detail.body().path("coursePayments").size());
        BigDecimal discount = BigDecimal.ZERO;
        for (var line : detail.body().path("coursePayments")) discount = discount.add(line.path("allocatedBillDiscountAmount").decimalValue());
        assertEquals(0, new BigDecimal("0.01").compareTo(discount));
        body.put("items", List.of(firstItem, secondItem));
        assertEquals(collection.body(), collect("multiple-course-receipt", body).body());
    }
}
