package com.sabyeguyz.schemacheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/PostgreSQL adjustment contracts, AC091–094 and 107.03/108.01–03.
 * Monetary source fixtures are synthetic; no assertion claims an actual bank transfer. */
class AdjustmentLifecycleHttpTest extends CoursePaymentHttpTestSupport {
    private static final String API = "/api/commission";
    private long staffId;

    @BeforeEach
    void resetAdjustmentFixture() {
        // This datasource belongs only to CoursePaymentHttpTestSupport's disposable container.
        db.execute("TRUNCATE TABLE staff_pay_adjustment, staff_monthly_payment, single_visit_fee_ledger CASCADE");
        staffId = db.queryForObject("select seller_staff_id from course where course_id=?", Long.class, courseId);
        db.update("update staff set staff_status=true,termination_date=null,hire_date=date '2025-01-01' where staff_id=?", staffId);
        db.update("update app_user set user_status=true where staff_id=?", staffId);
    }

    private Map<String, Object> correction(String amount) {
        var request = new LinkedHashMap<String, Object>();
        request.put("staffId", staffId);
        request.put("originalMonth", "2026-01");
        request.put("appliedMonth", "2026-02");
        request.put("amount", new BigDecimal(amount));
        request.put("reason", "Synthetic correction of previous source");
        request.put("courseId", courseId);
        return request;
    }

    private long create(String amount) {
        var response = reply("POST", API + "/adjustments", correction(amount));
        assertEquals(201, response.status(), response.body().toString());
        assertEquals("Pending", response.body().path("status").asText());
        money("0", response.body().path("processed"));
        money(new BigDecimal(amount).abs().toPlainString(), response.body().path("remaining"));
        return response.body().path("id").asLong();
    }

    private long approve(long id) {
        var response = reply("POST", API + "/adjustments/" + id + "/approve", Map.of());
        assertEquals(200, response.status(), response.body().toString());
        assertEquals("Approved", response.body().path("status").asText());
        return id;
    }

    private long application(long adjustmentId, String month, String amount) {
        var response = reply("POST", API + "/adjustments/" + adjustmentId + "/applications",
                Map.of("month", month, "amount", new BigDecimal(amount), "branchCode", "01"));
        assertEquals(201, response.status(), response.body().toString());
        assertEquals("Draft", response.body().path("status").asText());
        assertFalse(response.body().path("approved").asBoolean());
        return response.body().path("id").asLong();
    }

    private long paymentId(String month) {
        return db.queryForObject("select salary_payment_id from staff_monthly_payment where staff_id=? and branch_id=? and payment_month=?",
                Long.class, staffId, branchId, month);
    }

    private JsonNode closeAndPay(String month) {
        var closed = reply("POST", API + "/payments/close?month=" + month + "&branchCode=01", Map.of());
        assertEquals(200, closed.status(), closed.body().toString());
        var paid = reply("POST", API + "/payments/" + paymentId(month) + "/pay",
                Map.of("paidDate", "2026-02-28", "reference", "SYNTHETIC-TEST-ONLY"));
        assertEquals(200, paid.status(), paid.body().toString());
        assertEquals("PAID", paid.body().path("status").asText());
        return paid.body();
    }

    private JsonNode detail(long id) {
        var result = reply("GET", API + "/adjustments/" + id, null);
        assertEquals(200, result.status(), result.body().toString());
        return result.body();
    }

    private String paymentSnapshot(long id) {
        return db.queryForObject("select row_to_json(p)::text from staff_monthly_payment p where salary_payment_id=?", String.class, id);
    }

    private static void money(String expected, JsonNode actual) {
        assertTrue(actual.isNumber(), "Expected numeric amount, got " + actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()));
    }

    @Test
    void adjustmentAndApplicationApprovalsAreSeparateAndDeductionNeedsConsent() {
        long id = create("-1500");
        assertEquals(400, reply("POST", API + "/adjustments/" + id + "/applications",
                Map.of("month", "2026-02", "amount", 1500, "branchCode", "01")).status());
        approve(id);
        assertEquals(actorId, db.queryForObject("select approved_by from staff_pay_adjustment where adjustment_id=?", Long.class, id));
        assertNotNull(db.queryForObject("select approved_at from staff_pay_adjustment where adjustment_id=?", java.sql.Timestamp.class, id));
        long app = application(id, "2026-02", "1500");
        assertEquals(400, reply("POST", API + "/adjustment-applications/" + app + "/approve", Map.of("reference", " ")).status());
        assertNull(db.queryForObject("select approved_by from adjustment_application where application_id=?", Long.class, app));
        money("0", detail(id).path("processed"));
        money("1500", detail(id).path("remaining"));
        var consent = reply("POST", API + "/adjustment-applications/" + app + "/approve", Map.of("reference", "TEST-CONSENT-001"));
        assertEquals(200, consent.status(), consent.body().toString());
        assertEquals("Draft", consent.body().path("status").asText());
        assertTrue(consent.body().path("approved").asBoolean());
        assertEquals("TEST-CONSENT-001", consent.body().path("approvalReference").asText());
        assertEquals(actorId, db.queryForObject("select approved_by from adjustment_application where application_id=?", Long.class, app));
        assertNotNull(db.queryForObject("select approved_at from adjustment_application where application_id=?", java.sql.Timestamp.class, app));
        money("0", detail(id).path("processed"));
        assertEquals(0, db.queryForObject("select count(*) from staff_monthly_payment where payment_status='Paid'", Integer.class));
    }

    @Test
    void draftReservationsCannotOverbookAndCancellationRestoresAvailableBalance() {
        long id = approve(create("1500"));
        long first = application(id, "2026-02", "1000");
        int before = db.queryForObject("select count(*) from adjustment_application", Integer.class);
        assertEquals(400, reply("POST", API + "/adjustments/" + id + "/applications",
                Map.of("month", "2026-03", "amount", 501, "branchCode", "01")).status());
        assertEquals(before, db.queryForObject("select count(*) from adjustment_application", Integer.class));
        application(id, "2026-03", "500");
        var cancelled = reply("POST", API + "/adjustment-applications/" + first + "/cancel", Map.of());
        assertEquals(200, cancelled.status(), cancelled.body().toString());
        assertEquals("Cancelled", cancelled.body().path("status").asText());
        application(id, "2026-04", "1000");
        money("0", detail(id).path("processed"));
        money("1500", detail(id).path("remaining"));
        assertEquals(new BigDecimal("1500.00"), db.queryForObject(
                "select sum(abs(amount)) from adjustment_application where adjustment_id=? and application_status='Draft'", BigDecimal.class, id));
    }

    /** Source is a real persisted single-visit fee, so the 1000 limit is earned money, not a fabricated payment total. */
    private long earnedSingleVisitFee() {
        long visitId = db.queryForObject("insert into patient_visit(patient_id,branch_id,assigned_staff_id,treating_staff_id,visit_date,checkout_time,visit_status,visit_create_at,visit_create_by) "
                        + "values(?,?,?,?,timestamp '2026-02-10 03:00:00',timestamp '2026-02-10 04:00:00','Completed',CURRENT_TIMESTAMP,?) returning visit_id",
                Long.class, patientId, branchId, staffId, staffId, actorId);
        long ruleId = db.queryForObject("select treatment_fee_rule_id from treatment_fee_rule order by treatment_fee_rule_id limit 1", Long.class);
        return db.queryForObject("insert into single_visit_fee_ledger(visit_id,staff_id,treatment_fee_rule_id,service_date,fee_type,fee_value,calculation_base,fee_amount,entry_type,created_at,created_by,fee_version) "
                        + "values(?,?,?,date '2026-02-10','Fixed',1000,1000,1000,'Allocation',CURRENT_TIMESTAMP,?,1) returning fee_id",
                Long.class, visitId, staffId, ruleId, actorId);
    }

    @Test
    void earned1000Caps1500DeductionAndResignationPreserves500ForExternalReview() {
        long feeId = earnedSingleVisitFee();
        var request = correction("-1500");
        request.remove("courseId");
        request.put("singleVisitFeeId", feeId);
        var created = reply("POST", API + "/adjustments", request);
        assertEquals(201, created.status(), created.body().toString());
        long id = approve(created.body().path("id").asLong());
        long app = application(id, "2026-02", "1500");
        assertEquals(200, reply("POST", API + "/adjustment-applications/" + app + "/approve", Map.of("reference", "TEST-CONSENT-002")).status());
        JsonNode paid = closeAndPay("2026-02");
        money("1000", paid.path("singleVisitFee"));
        money("-1000", paid.path("adjustment"));
        money("0", paid.path("net"));
        long paidId = paid.path("id").asLong();
        String persistedPayment = paymentSnapshot(paidId);
        // PostgreSQL stores microsecond timestamps; compare two persisted reads rather than the pre-flush nanosecond response.
        JsonNode persistedPaidResponse = reply("GET", API + "/payments/" + paidId, null).body();
        JsonNode partial = detail(id);
        assertEquals("PartiallyApplied", partial.path("status").asText());
        money("1000", partial.path("processed"));
        money("500", partial.path("remaining"));
        money("-1000", partial.path("applications").get(0).path("amount"));
        assertEquals("Applied", partial.path("applications").get(0).path("status").asText());
        long remainingDraft = application(id, "2026-03", "500");
        assertEquals(400, reply("POST", API + "/adjustments/" + id + "/external-review", Map.of()).status());
        assertEquals(200, reply("POST", "/api/staff/" + staffId + "/terminate", Map.of("terminationDate", "2026-02-28")).status());
        var reviewed = reply("POST", API + "/adjustments/" + id + "/external-review", Map.of());
        assertEquals(200, reviewed.status(), reviewed.body().toString());
        assertEquals("ExternalReview", reviewed.body().path("status").asText());
        money("-1500", reviewed.body().path("amount"));
        money("1000", reviewed.body().path("processed"));
        money("500", reviewed.body().path("remaining"));
        assertEquals(feeId, reviewed.body().path("singleVisitFeeId").asLong());
        assertFalse(reviewed.body().path("reason").asText().isBlank());
        assertEquals("Cancelled", db.queryForObject("select application_status::text from adjustment_application where application_id=?", String.class, remainingDraft));
        assertEquals(1, db.queryForObject("select count(*) from audit_log where table_name='staff_pay_adjustment' and record_id=? and new_value::jsonb->>'summary'='ExternalReview' and changed_by=? and changed_at is not null", Integer.class, String.valueOf(id), actorId));
        int paymentCount = db.queryForObject("select count(*) from staff_monthly_payment", Integer.class);
        for (int i=0;i<2;i++) {
            detail(id);
            var oldPaid = reply("GET", API + "/payments/" + paid.path("id").asLong(), null);
            assertEquals(200, oldPaid.status(), oldPaid.body().toString());
            assertEquals(persistedPaidResponse, oldPaid.body(), "Read after resignation must preserve the paid snapshot");
            assertEquals(persistedPayment, paymentSnapshot(paidId), "No persisted payout column may change during resignation/review/reads");
        }
        assertEquals(paymentCount, db.queryForObject("select count(*) from staff_monthly_payment", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from staff_monthly_payment where staff_id=? and payment_status='Paid'", Integer.class, staffId));
        assertEquals(400, reply("POST", API + "/adjustments/" + id + "/external-review", Map.of()).status());
        money("500", detail(id).path("remaining"));
    }

    @Test
    void positiveCorrectionIsAppliedOnlyAtPaymentAndPaidRequestsCannotApplyTwice() {
        String courseBefore = db.queryForObject("select row_to_json(c)::text from course c where course_id=?", String.class, courseId);
        int sales = db.queryForObject("select count(*) from sales_transaction", Integer.class);
        long id = approve(create("250"));
        long app = application(id, "2026-02", "250");
        var approved = reply("POST", API + "/adjustment-applications/" + app + "/approve", Map.of());
        assertEquals(200, approved.status(), approved.body().toString());
        money("0", detail(id).path("processed"));
        JsonNode paid = closeAndPay("2026-02");
        money("250", paid.path("net"));
        money("250", paid.path("adjustment"));
        long paidId = paid.path("id").asLong();
        String persistedPayment = paymentSnapshot(paidId);
        JsonNode persistedPaidResponse = reply("GET", API + "/payments/" + paidId, null).body();
        var done = detail(id);
        assertEquals("Applied", done.path("status").asText());
        money("250", done.path("processed"));
        money("0", done.path("remaining"));
        assertEquals(409, reply("POST", API + "/payments/" + paid.path("id").asLong() + "/pay", Map.of()).status());
        assertEquals(400, reply("POST", API + "/adjustment-applications/" + app + "/cancel", Map.of()).status());
        assertEquals(400, reply("POST", API + "/adjustments/" + id + "/applications", Map.of("month", "2026-03", "amount", 1, "branchCode", "01")).status());
        assertEquals(persistedPaidResponse, reply("GET", API + "/payments/" + paidId, null).body());
        assertEquals(persistedPayment, paymentSnapshot(paidId));
        assertEquals(courseBefore, db.queryForObject("select row_to_json(c)::text from course c where course_id=?", String.class, courseId));
        assertEquals(sales, db.queryForObject("select count(*) from sales_transaction", Integer.class));
        assertEquals(1, db.queryForObject("select count(*) from adjustment_application where adjustment_id=? and application_status='Applied'", Integer.class, id));
    }

    @Test
    void invalidSourceMonthAmountAndTherapistRequestsDoNotCreateCorrections() {
        for (String field : new String[]{"staffId", "amount", "reason", "courseId"}) {
            var invalid = correction("100");
            invalid.remove(field);
            assertEquals(400, reply("POST", API + "/adjustments", invalid).status(), field);
        }
        var wrongMonth = correction("100");
        wrongMonth.put("appliedMonth", "2026-01");
        assertEquals(400, reply("POST", API + "/adjustments", wrongMonth).status());
        var zero = correction("0");
        assertEquals(400, reply("POST", API + "/adjustments", zero).status());
        String therapist = login("therapist", "therapist123");
        assertEquals(403, replyAs(therapist, "POST", API + "/adjustments", correction("100")).status());
        assertEquals(403, replyAs(therapist, "GET", API + "/adjustments", null).status());
        assertEquals(0, db.queryForObject("select count(*) from staff_pay_adjustment", Integer.class));
        assertEquals(0, db.queryForObject("select count(*) from adjustment_application", Integer.class));
    }
}
