package com.sabyeguyz.schemacheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP refund boundaries and shared cancellation contracts. */
class RefundHttpContractTest extends CoursePaymentHttpTestSupport {
    private long originalPayment() {
        return db.queryForObject("select p.payment_id from payment_transaction p join course c using(receipt_id) where c.course_id=?",
                Long.class, courseId);
    }

    private long collectionPayment() {
        return db.queryForObject("select p.payment_id from payment_transaction p join receipt_course_payment c using(receipt_id) where c.course_id=?",
                Long.class, courseId);
    }

    private Map<String, Object> request(long course, int visits, Long mainItem) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("courseId", course);
        item.put("refundAmount", 100);
        item.put("cancelledVisits", mainItem == null ? visits : null);
        item.put("rightsCancellationItemId", mainItem);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", 100);
        body.put("paymentMethodCode", "PromptPay");
        body.put("reason", "Patient requested refund");
        body.put("items", List.of(item));
        return body;
    }

    private JsonNode create(long payment, Map<String, Object> payload) throws Exception {
        var response = reply("POST", "/api/payments/" + payment + "/refunds", payload);
        assertEquals(201, response.status(), response.body().toString());
        return response.body();
    }

    private int remaining() {
        return db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId);
    }

    @Test
    void selectedReasonCodeIsPersistedAndAuditedWhileLegacyDefaultsToOther() throws Exception {
        var payload = request(courseId, 2, null);
        payload.put("reasonCode", "PatientRequested");
        var selected = create(originalPayment(), payload);
        assertEquals("PatientRequested", db.queryForObject("select c.reason_code::text from transaction_cancellation c join refund_transaction r using(cancellation_id) where r.refund_id=?",
                String.class, selected.path("id").asLong()));
        assertEquals(1, db.queryForObject("select count(*) from audit_log where table_name='refund_transaction' and record_id=? and new_value::jsonb->>'reason_code'='PatientRequested'",
                Integer.class, selected.path("id").asText()));
        var legacy = create(collectionPayment(), request(courseId, 1, null));
        assertEquals("Other", db.queryForObject("select c.reason_code::text from transaction_cancellation c join refund_transaction r using(cancellation_id) where r.refund_id=?",
                String.class, legacy.path("id").asLong()));
    }

    @Test
    void blankOversizedAndInvalidReasonsReturn400WithoutCreatingRefunds() throws Exception {
        long count = db.queryForObject("select count(*) from refund_transaction", Long.class);
        for (String reason : List.of(" ", "x".repeat(256))) {
            var payload = request(courseId, 1, null);
            payload.put("reason", reason);
            assertEquals(400, reply("POST", "/api/payments/" + originalPayment() + "/refunds", payload).status());
        }
        var invalidCode = request(courseId, 1, null);
        invalidCode.put("reasonCode", "Unknown");
        assertEquals(400, reply("POST", "/api/payments/" + originalPayment() + "/refunds", invalidCode).status());
        assertEquals(count, db.queryForObject("select count(*) from refund_transaction", Long.class));
    }

    @Test
    void everyRefundActionRemainsManagerOnly() throws Exception {
        var refund = create(originalPayment(), request(courseId, 1, null));
        String therapist = login("therapist", "therapist123");
        assertEquals(403, replyAs(therapist, "GET", "/api/payments/" + originalPayment() + "/refunds", null).status());
        assertEquals(403, replyAs(therapist, "POST", "/api/payments/" + originalPayment() + "/refunds", request(courseId, 1, null)).status());
        for (String action : List.of("complete", "void", "fail", "retry")) {
            assertEquals(403, replyAs(therapist, "POST", "/api/refunds/" + refund.path("id").asLong() + "/" + action, null).status());
        }
    }

    @Test
    void closedOriginalPaymentBranchRejectsReadsAndAllMutations() throws Exception {
        var refund = create(originalPayment(), request(courseId, 1, null));
        db.update("update branch set branch_status=false where branch_id=?", branchId);
        assertEquals(403, reply("GET", "/api/payments/" + originalPayment() + "/refunds", null).status());
        assertEquals(403, reply("POST", "/api/payments/" + originalPayment() + "/refunds", request(courseId, 1, null)).status());
        for (String action : List.of("complete", "void", "fail", "retry")) {
            assertEquals(403, reply("POST", "/api/refunds/" + refund.path("id").asLong() + "/" + action, null).status());
        }
        assertEquals("Pending", db.queryForObject("select refund_status::text from refund_transaction where refund_id=?", String.class, refund.path("id").asLong()));
    }

    @Test
    void completingRelatedRefundFirstCutsOnceAndKeepsActualApprovers() throws Exception {
        var main = create(originalPayment(), request(courseId, 2, null));
        long mainItem = main.path("items").get(0).path("courseRefundItemId").asLong();
        var child = create(collectionPayment(), request(courseId, 0, mainItem));
        assertTrue(child.path("items").get(0).path("cancelledVisits").isNull());
        assertEquals(mainItem, child.path("items").get(0).path("rightsCancellationItemId").asLong());
        assertEquals(200, reply("POST", "/api/refunds/" + child.path("id").asLong() + "/complete", null).status());
        assertEquals(8, remaining());
        assertEquals(200, reply("POST", "/api/refunds/" + main.path("id").asLong() + "/complete", null).status());
        assertEquals(8, remaining());
        assertEquals(-2, db.queryForObject("select sum(qty_change) from course_ledger where course_refund_item_id=? and entry_type='Cancellation'", Integer.class, mainItem));
        assertEquals(0, db.queryForObject("select count(*) from course_ledger where course_refund_item_id=?", Integer.class,
                child.path("items").get(0).path("courseRefundItemId").asLong()));
        assertEquals(actorId, db.queryForObject("select approved_by from refund_transaction where refund_id=?", Long.class, child.path("id").asLong()));
        assertEquals(400, reply("POST", "/api/refunds/" + child.path("id").asLong() + "/void", null).status());
    }

    @Test
    void voidedMainStillAllowsRelatedCompletionWithoutChangingMainApproval() throws Exception {
        var main = create(originalPayment(), request(courseId, 2, null));
        long mainItem = main.path("items").get(0).path("courseRefundItemId").asLong();
        var child = create(collectionPayment(), request(courseId, 0, mainItem));
        assertEquals(200, reply("POST", "/api/refunds/" + main.path("id").asLong() + "/void", null).status());
        assertEquals(200, reply("POST", "/api/refunds/" + child.path("id").asLong() + "/complete", null).status());
        assertEquals(8, remaining());
        assertEquals("Voided", db.queryForObject("select refund_status::text from refund_transaction where refund_id=?", String.class, main.path("id").asLong()));
        assertNull(db.queryForObject("select approved_by from refund_transaction where refund_id=?", Long.class, main.path("id").asLong()));
        assertEquals(0, db.queryForObject("select count(*) from course_ledger where course_refund_item_id=? and entry_type='Reversal'", Integer.class, mainItem));
    }

    @Test
    void failedRelatedRetryKeepsGroupAndDoesNotCutRightsAgain() throws Exception {
        var main = create(originalPayment(), request(courseId, 8, null));
        long mainItem = main.path("items").get(0).path("courseRefundItemId").asLong();
        var child = create(collectionPayment(), request(courseId, 0, mainItem));
        assertEquals(200, reply("POST", "/api/refunds/" + main.path("id").asLong() + "/complete", null).status());
        assertEquals(200, reply("POST", "/api/refunds/" + child.path("id").asLong() + "/fail", null).status());
        assertEquals(200, reply("POST", "/api/refunds/" + child.path("id").asLong() + "/retry", null).status());
        assertEquals(200, reply("POST", "/api/refunds/" + child.path("id").asLong() + "/complete", null).status());
        assertEquals(2, remaining());
    }

    @Test
    void concurrentMainAndRelatedCompletionCutRightsOnce() throws Exception {
        var main = create(originalPayment(), request(courseId, 2, null));
        long mainItem = main.path("items").get(0).path("courseRefundItemId").asLong();
        var child = create(collectionPayment(), request(courseId, 0, mainItem));
        var workers = Executors.newFixedThreadPool(2);
        try {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var first = workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return reply("POST", "/api/refunds/" + main.path("id").asLong() + "/complete", null);
            });
            var second = workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return reply("POST", "/api/refunds/" + child.path("id").asLong() + "/complete", null);
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(200, first.get(30, TimeUnit.SECONDS).status());
            assertEquals(200, second.get(30, TimeUnit.SECONDS).status());
        } finally {
            workers.shutdownNow();
        }
        assertEquals(8, remaining());
        assertEquals(-2, db.queryForObject("select sum(qty_change) from course_ledger where course_refund_item_id=? and entry_type='Cancellation'", Integer.class, mainItem));
    }

    @Test
    void invalidReferencesAndPaymentCeilingsAreRejectedAndUncutVoidsCreateNoReversal() throws Exception {
        var main = create(originalPayment(), request(courseId, 2, null));
        long mainItem = main.path("items").get(0).path("courseRefundItemId").asLong();
        var child = create(collectionPayment(), request(courseId, 0, mainItem));
        long childItem = child.path("items").get(0).path("courseRefundItemId").asLong();
        assertEquals(400, reply("POST", "/api/payments/" + collectionPayment() + "/refunds", request(courseId, 0, childItem)).status());
        var over = request(courseId, 1, null);
        over.put("amount", 1000);
        assertEquals(400, reply("POST", "/api/payments/" + collectionPayment() + "/refunds", over).status());
        assertEquals(200, reply("POST", "/api/refunds/" + main.path("id").asLong() + "/void", null).status());
        assertEquals(200, reply("POST", "/api/refunds/" + child.path("id").asLong() + "/void", null).status());
        assertEquals(10, remaining());
        assertEquals(0, db.queryForObject("select count(*) from course_ledger where course_refund_item_id=? and entry_type='Reversal'", Integer.class, mainItem));
    }
}
