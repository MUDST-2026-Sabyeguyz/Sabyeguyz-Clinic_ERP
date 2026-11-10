package com.sabyeguyz.schemacheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptDocumentHttpTest extends CoursePaymentHttpTestSupport {
    private long collectionReceipt() {
        return db.queryForObject("select receipt_id from receipt_course_payment where course_id=?", Long.class, courseId);
    }

    private long purchaseReceipt() {
        return db.queryForObject("select receipt_id from course where course_id=?", Long.class, courseId);
    }

    @Test
    void readsCollectionOnlyWithPrintInformationAndStoredTotalsWithoutChangingLegacyIdentity() {
        long receiptId = collectionReceipt();
        var response = reply("GET", "/api/receipt-documents/" + receiptId, null);
        assertEquals(200, response.status());
        JsonNode document = response.body();
        assertEquals(receiptId, document.path("receiptId").asLong());
        assertEquals(0, document.path("sales").size());
        assertEquals(1, document.path("coursePayments").size());
        assertEquals(courseId.longValue(), document.path("coursePayments").get(0).path("courseId").asLong());
        assertEquals(900, document.path("netAmount").decimalValue().intValueExact());
        assertEquals(100, document.path("billDiscountAmount").decimalValue().intValueExact());
        assertEquals("Completed", document.path("payments").get(0).path("status").asText());
        assertEquals("PromptPay", document.path("payments").get(0).path("paymentMethod").asText());
        assertFalse(document.path("payments").get(0).path("paymentDate").asText().isBlank());
        assertFalse(document.path("patient").path("hn").asText().isBlank());
        assertFalse(document.path("patient").path("fullName").asText().isBlank());
        assertEquals("01", document.path("branch").path("code").asText());
        assertFalse(document.path("branch").path("name").asText().isBlank());
        assertFalse(document.has("taxAmount"));

        long legacySaleId = db.queryForObject("select sale_transaction_id from course where course_id=?", Long.class, courseId);
        assertEquals(200, reply("GET", "/api/receipts/" + legacySaleId, null).status());
        assertEquals(0, db.queryForObject("select count(*) from sales_transaction where transaction_id=?", Integer.class, receiptId));
        assertEquals(400, reply("GET", "/api/receipts/" + receiptId, null).status());
    }

    @Test
    void searchAndCourseHistoryContainPurchaseAndCollectionExactlyOnceAndIncludePaymentHistory() {
        var search = reply("GET", "/api/receipt-documents?patientId=" + patientId + "&page=0&size=1", null);
        assertEquals(200, search.status());
        assertEquals(1, search.body().path("items").size());
        assertEquals(2, search.body().path("totalItems").asInt());
        assertEquals(2, search.body().path("totalPages").asInt());
        assertEquals(0, search.body().path("page").asInt());
        assertEquals(1, search.body().path("items").get(0).path("payments").size());

        var history = reply("GET", "/api/courses/" + courseId + "/receipt-history", null);
        assertEquals(200, history.status());
        assertEquals(2, history.body().size());
        assertEquals(purchaseReceipt(), history.body().get(0).path("receiptId").asLong());
        assertEquals(collectionReceipt(), history.body().get(1).path("receiptId").asLong());
        db.update("update course set course_name='Changed template name' where course_id=?", courseId);
        var detail = reply("GET", "/api/receipt-documents/" + collectionReceipt(), null);
        assertEquals("Shared refund fixture course", detail.body().path("coursePayments").get(0).path("itemNameSnapshot").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "patientId=0"})
    void rejectsInvalidPaginationAndPatientFilter(String query) {
        assertEquals(400, reply("GET", "/api/receipt-documents?" + query, null).status());
    }

    @Test
    void enforcesAuthenticationBranchScopeAndFailAllCourseHistory() {
        assertEquals(401, replyAs(null, "GET", "/api/receipt-documents", null).status());
        String therapist = login("therapist", "therapist123");
        assertEquals(200, replyAs(therapist, "GET", "/api/receipt-documents/" + collectionReceipt(), null).status());
        long otherBranch = db.queryForObject("select branch_id from branch where branch_code='02'", Long.class);
        db.update("update receipt set branch_id=? where receipt_id=?", otherBranch, collectionReceipt());
        assertEquals(403, replyAs(therapist, "GET", "/api/receipt-documents/" + collectionReceipt(), null).status());
        assertEquals(403, replyAs(therapist, "GET", "/api/courses/" + courseId + "/receipt-history", null).status());
        assertEquals(403, replyAs(therapist, "GET", "/api/receipt-documents?branchCode=02", null).status());
        assertEquals(200, reply("GET", "/api/courses/" + courseId + "/receipt-history", null).status());
        db.update("update branch set branch_status=false where branch_id=?", otherBranch);
        assertEquals(403, reply("GET", "/api/receipt-documents/" + collectionReceipt(), null).status());
    }

    @Test
    void newDocumentRouteReturnsNotFoundForMissingReceipt() {
        assertEquals(404, reply("GET", "/api/receipt-documents/99999999", null).status());
        assertEquals(400, reply("GET", "/api/receipt-documents/0", null).status());
    }

    @Test
    void voidsOnlyItsReceiptPaymentWithReasonAndAuditWithoutChangingCourseRights() {
        int remaining = db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId);
        int ledger = db.queryForObject("select count(*) from course_ledger where course_id=?", Integer.class, courseId);
        long receiptId = collectionReceipt();
        var result = reply("POST", "/api/receipt-documents/" + receiptId + "/void",
                Map.of("reasonCode", "PatientRequested", "reason", "Customer requested cancellation"));
        assertEquals(200, result.status());
        assertEquals(receiptId, result.body().path("receiptId").asLong());
        assertTrue(result.body().path("cancellationId").asLong() > 0);
        assertEquals("Voided", reply("GET", "/api/receipt-documents/" + receiptId, null).body().path("receiptStatus").asText());
        assertEquals("Issued", reply("GET", "/api/receipt-documents/" + purchaseReceipt(), null).body().path("receiptStatus").asText());
        assertEquals(remaining, db.queryForObject("select remaining_visits from course where course_id=?", Integer.class, courseId));
        assertEquals(ledger, db.queryForObject("select count(*) from course_ledger where course_id=?", Integer.class, courseId));
        assertEquals("PatientRequested", db.queryForObject("select reason_code::text from transaction_cancellation where cancellation_id=?",
                String.class, result.body().path("cancellationId").asLong()));
        assertTrue(db.queryForObject("select count(*) from audit_log where table_name='receipt' and record_id=?", Integer.class, Long.toString(receiptId)) > 0);
        assertEquals(400, reply("POST", "/api/receipt-documents/" + receiptId + "/void",
                Map.of("reasonCode", "Other", "reason", "Again")).status());
    }

    @Test
    void rejectsInvalidVoidReasonOriginalPurchaseAndActiveRefund() {
        String route = "/api/receipt-documents/" + collectionReceipt() + "/void";
        assertEquals(400, reply("POST", route, Map.of("reason", "reason")).status());
        assertEquals(400, reply("POST", route, Map.of("reasonCode", "Other", "reason", "  ")).status());
        assertEquals(400, reply("POST", route, Map.of("reasonCode", "Other", "reason", "x".repeat(256))).status());
        assertEquals(400, reply("POST", route, Map.of("reasonCode", "INVALID", "reason", "reason")).status());
        assertEquals(400, reply("POST", "/api/receipt-documents/" + purchaseReceipt() + "/void",
                Map.of("reasonCode", "Other", "reason", "reason")).status());

        long payment = db.queryForObject("select payment_id from payment_transaction where receipt_id=?", Long.class, collectionReceipt());
        var refund = reply("POST", "/api/payments/" + payment + "/refunds", Map.of(
                "amount", 10, "paymentMethodCode", "PromptPay", "reason", "Refund pending",
                "items", java.util.List.of(Map.of("courseId", courseId, "refundAmount", 10, "cancelledVisits", 1))));
        assertEquals(201, refund.status());
        assertEquals(400, reply("POST", route, Map.of("reasonCode", "Other", "reason", "reason")).status());
    }

    @Test
    void voidRequiresPermissionAndReceiptBranchAccess() {
        long permission = db.queryForObject("select permission_id from permission where permission_name='SALE_CANCEL'", Long.class);
        var previous = db.queryForList("select is_allowed from user_permission where user_id=? and permission_id=?",
                Boolean.class, actorId, permission);
        db.update("insert into user_permission(user_id,permission_id,is_allowed,updated_by,updated_at) values(?,?,false,?,CURRENT_TIMESTAMP) "
                + "on conflict(user_id,permission_id) do update set is_allowed=false", actorId, permission, actorId);
        String route = "/api/receipt-documents/" + collectionReceipt() + "/void";
        try {
            assertEquals(403, reply("POST", route, Map.of("reasonCode", "Other", "reason", "No permission")).status());
        } finally {
            if (previous.isEmpty()) {
                db.update("delete from user_permission where user_id=? and permission_id=?", actorId, permission);
            } else {
                db.update("update user_permission set is_allowed=? where user_id=? and permission_id=?", previous.get(0), actorId, permission);
            }
        }
        long otherBranch = db.queryForObject("select branch_id from branch where branch_code='02'", Long.class);
        db.update("update receipt set branch_id=? where receipt_id=?", otherBranch, collectionReceipt());
        assertEquals(403, replyAs(login("therapist", "therapist123"), "POST", route,
                Map.of("reasonCode", "Other", "reason", "Other branch")).status());
    }

    @Test
    void mixedReceiptCannotUseCollectionOnlyVoid() {
        db.update("insert into sales_transaction(receipt_id,line_no,patient_id,branch_id,service_id,transaction_type,"
                + "salesperson_staff_id,quantity,unit_price,amount,line_discount_amount,allocated_bill_discount_amount,"
                + "commissionable_amount,item_name_snapshot,unit_name_snapshot,transaction_date,transaction_status,transaction_create_at,transaction_create_by) "
                + "select ?,line_no,patient_id,branch_id,service_id,transaction_type,salesperson_staff_id,quantity,unit_price,amount,"
                + "line_discount_amount,allocated_bill_discount_amount,commissionable_amount,item_name_snapshot,unit_name_snapshot,"
                + "transaction_date,transaction_status,transaction_create_at,transaction_create_by from sales_transaction where receipt_id=?",
                collectionReceipt(), purchaseReceipt());
        assertEquals(400, reply("POST", "/api/receipt-documents/" + collectionReceipt() + "/void",
                Map.of("reasonCode", "Other", "reason", "Mixed receipt")).status());
    }

    @Test
    void simultaneousRefundAndCollectionVoidCannotBothSucceed() throws Exception {
        long receiptId = collectionReceipt();
        long payment = db.queryForObject("select payment_id from payment_transaction where receipt_id=?", Long.class, receiptId);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var voidResult = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                return reply("POST", "/api/receipt-documents/" + receiptId + "/void",
                        Map.of("reasonCode", "Other", "reason", "Concurrent void"));
            });
            var refundResult = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                return reply("POST", "/api/payments/" + payment + "/refunds", Map.of(
                        "amount", 10, "paymentMethodCode", "PromptPay", "reason", "Concurrent refund",
                        "items", java.util.List.of(Map.of("courseId", courseId, "refundAmount", 10, "cancelledVisits", 1))));
            });
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            int voidStatus = voidResult.get(45, java.util.concurrent.TimeUnit.SECONDS).status();
            int refundStatus = refundResult.get(45, java.util.concurrent.TimeUnit.SECONDS).status();
            assertTrue((voidStatus == 200 && refundStatus == 400) || (voidStatus == 400 && refundStatus == 201),
                    "Exactly one financial action must succeed: void=" + voidStatus + ", refund=" + refundStatus);
            boolean completedPayment = db.queryForObject("select payment_status from payment_transaction where payment_id=?", Boolean.class, payment);
            int activeRefunds = db.queryForObject("select count(*) from refund_transaction where original_payment_id=? and refund_status='Pending'", Integer.class, payment);
            assertEquals(voidStatus == 400, completedPayment);
            assertEquals(refundStatus == 201 ? 1 : 0, activeRefunds);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
