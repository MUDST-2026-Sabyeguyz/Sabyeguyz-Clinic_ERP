package com.sabyeguyz.schemacheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** US110/111 and collection invariants of US114.04, real HTTP on disposable PostgreSQL.
 * SQL only positions already-created events on exact historical reporting dates. */
class ReportTransactionsReconciliationHttpTest extends CoursePaymentHttpTestSupport {
    private static final String SALE_DAY = "?from=2034-02-01&to=2034-02-01&branchCode=01";
    private static final String CASH_DAY = "?from=2034-02-02&to=2034-02-02&branchCode=01";

    private JsonNode report(String type, String filters) {
        var result = reply("GET", "/api/reports/" + type + "-detail" + filters, null);
        assertEquals(200, result.status(), result.body().toString());
        return result.body();
    }
    private static void money(String expected, JsonNode actual) {
        assertTrue(actual.isNumber(), actual.toString());
        assertEquals(0, new BigDecimal(expected).compareTo(actual.decimalValue()), actual.toString());
    }
    private HttpResult collect(String key, String method, String amount) {
        var response = replyWithHeaders(token, "POST", "/api/course-payment-collections", Map.of(
                "patientId", patientId, "branchCode", "01", "items", List.of(Map.of(
                        "courseId", courseId, "amount", new BigDecimal(amount))),
                "paymentLabelCode", "INSTALLMENT", "paymentMethodCode", method,
                "paymentAmount", new BigDecimal(amount)), Map.of("Idempotency-Key", key));
        assertEquals(201, response.status(), response.body().toString());
        db.update("update payment_transaction set payment_date='2034-02-02T03:00:00Z' where payment_id=?",
                response.body().path("paymentId").asLong());
        return response;
    }
    private void positionOriginalCourseSale() {
        db.update("update receipt set sale_date='2034-02-01T10:00:00' where receipt_id=(select receipt_id from course where course_id=?)", courseId);
        db.update("update sales_transaction set transaction_date='2034-02-01T03:00:00Z' where transaction_id=(select sale_transaction_id from course where course_id=?)", courseId);
        db.update("update payment_transaction set payment_date='2034-02-02T03:00:00Z' where receipt_id=(select receipt_id from course where course_id=?)", courseId);
    }

    @Test void newCourseAndServiceSaleThenLaterCollectionUseIndependentDatesAndExactFilteredTotals() {
        var createdService = reply("POST", "/api/services", Map.of("code", "REPORT_SINGLE", "name", "Report single service",
                "price", 1000, "defaultDurationMinutes", 30));
        assertEquals(201, createdService.status(), createdService.body().toString());
        long service = createdService.body().path("id").asLong();
        long therapist = db.queryForObject("select staff_id from staff where staff_code='THP-001'", Long.class);
        long template = db.queryForObject("select service_id from service where service_code='COURSE_10'", Long.class);
        BigDecimal coursePrice = db.queryForObject("select service_price from service where service_id=?", BigDecimal.class, template);
        db.update("update branch set opening_time='09:00',closing_time='18:00',default_appointment_minutes=30 where branch_id=?", branchId);
        var booking = reply("POST", "/api/appointments", Map.of("branchCode", "01", "patientId", patientId,
                "therapistId", therapist, "serviceId", service, "patientType", "CURRENT",
                "startAt", "2040-02-06T10:00:00+07:00", "endAt", "2040-02-06T10:30:00+07:00"));
        assertEquals(201, booking.status(), booking.body().toString());
        BigDecimal total = coursePrice.add(new BigDecimal("1000"));
        var sale = reply("POST", "/api/payments", Map.of("patientId", patientId, "branchCode", "01",
                "salespersonStaffId", therapist, "treatingStaffId", therapist, "appointmentId", booking.body().path("id").asLong(),
                "paymentLabelCode", "FULL", "items", List.of(
                        Map.of("type", "COURSE", "referenceId", template, "quantity", 1),
                        Map.of("type", "SERVICE", "referenceId", service, "quantity", 1)),
                "payments", List.of(Map.of("paymentMethodCode", "PromptPay", "amount", total))));
        assertEquals(201, sale.status(), sale.body().toString());
        long receipt = sale.body().path("receiptId").asLong();
        db.update("update receipt set sale_date='2034-02-01T10:00:00' where receipt_id=?", receipt);
        db.update("update payment_transaction set payment_date='2034-02-02T03:00:00Z' where receipt_id=?", receipt);
        courseId = db.queryForObject("select course_id from course where receipt_id=?", Long.class, receipt);
        var beforeSales = report("sales", SALE_DAY);
        assertEquals(2, beforeSales.path("rows").size());
        money(total.toPlainString(), beforeSales.path("total"));
        assertEquals(0, report("payments", SALE_DAY).path("rows").size());
        assertEquals(0, report("sales", CASH_DAY).path("rows").size());
        var paid = report("payments", CASH_DAY);
        assertEquals(2, paid.path("rows").size());
        money(total.toPlainString(), paid.path("total"));
        money("1000", report("sales", SALE_DAY + "&saleType=SingleVisit&treatment=Report%20single").path("total"));
        money(coursePrice.toPlainString(), report("sales", SALE_DAY + "&saleType=CoursePurchase").path("total"));
        long salesCount = db.queryForObject("select count(*) from sales_transaction", Long.class);
        BigDecimal commissionBase = db.queryForObject("select commissionable_amount from sales_transaction where transaction_id=(select sale_transaction_id from course where course_id=?)", BigDecimal.class, courseId);
        collect("reconcile-new-course", "PromptPay", "25");
        assertEquals(beforeSales, report("sales", SALE_DAY), "Later collection must leave original sale report unchanged");
        money(total.add(new BigDecimal("25")).toPlainString(), report("payments", CASH_DAY).path("total"));
        assertEquals(salesCount, db.queryForObject("select count(*) from sales_transaction", Long.class));
        assertEquals(commissionBase, db.queryForObject("select commissionable_amount from sales_transaction where transaction_id=(select sale_transaction_id from course where course_id=?)", BigDecimal.class, courseId));
    }

    @Test void paymentChannelsAndEveryFilterUseExactlyTheSameRowsAsTotals() {
        db.update("update payment_method set payment_method_status=true where method_name::text in ('CreditCard','PromptPay','BankTransfer')");
        collect("channels-credit", "CreditCard", "11.11");
        collect("channels-prompt", "PromptPay", "22.22");
        collect("channels-bank", "BankTransfer", "33.33");
        var cash = report("payments", CASH_DAY);
        assertEquals(3, cash.path("rows").size());
        money("66.66", cash.path("total"));
        money("11.11", cash.path("byMethod").path("CreditCard"));
        money("22.22", cash.path("byMethod").path("PromptPay"));
        money("33.33", cash.path("byMethod").path("BankTransfer"));
        String hn = db.queryForObject("select patient_hn from patient where patient_id=?", String.class, patientId);
        var matching = report("payments", CASH_DAY + "&patient=" + java.net.URLEncoder.encode(hn, java.nio.charset.StandardCharsets.UTF_8) + "&saleType=CoursePurchase&treatment=Shared%20refund");
        assertEquals(cash, matching);
        for (String query : List.of(CASH_DAY + "&patient=NO_MATCH", CASH_DAY + "&saleType=SingleVisit", CASH_DAY + "&treatment=NO_MATCH",
                "?from=2034-02-02&to=2034-02-02&branchCode=02", SALE_DAY)) {
            var empty = report("payments", query);
            assertEquals(0, empty.path("rows").size()); money("0", empty.path("total"));
            assertEquals(0, empty.path("byMethod").size()); money("0", empty.path("refundedTotal"));
        }
    }

    @Test void onlyCompletedRefundIsMoneyOutAndItNeverReducesOrDuplicatesGrossCash() {
        positionOriginalCourseSale();
        long payment = db.queryForObject("select p.payment_id from payment_transaction p join course c using(receipt_id) where c.course_id=?", Long.class, courseId);
        var requested = reply("POST", "/api/payments/" + payment + "/refunds", Map.of("amount", 100,
                "paymentMethodCode", "PromptPay", "reason", "Report refund reconciliation", "reasonCode", "PatientRequested",
                "items", List.of(Map.of("courseId", courseId, "refundAmount", 100, "cancelledVisits", 1))));
        assertEquals(201, requested.status(), requested.body().toString());
        long refund = requested.body().path("id").asLong();
        money("0", report("payments", CASH_DAY).path("refundedTotal"));
        assertEquals(200, reply("POST", "/api/refunds/" + refund + "/fail", null).status());
        money("0", report("payments", CASH_DAY).path("refundedTotal"));
        assertEquals(200, reply("POST", "/api/refunds/" + refund + "/retry", null).status());
        assertEquals(200, reply("POST", "/api/refunds/" + refund + "/complete", null).status());
        db.update("update refund_transaction set refunded_at='2034-02-02T04:00:00Z' where refund_id=?", refund);
        var cash = report("payments", CASH_DAY);
        money("10000", cash.path("total")); money("100", cash.path("refundedTotal"));
        assertEquals(1, cash.path("refunds").size());
        assertEquals(payment, cash.path("refunds").get(0).path("originalPaymentId").asLong());
        assertEquals("PromptPay", cash.path("refunds").get(0).path("paymentMethod").asText());
        assertEquals(cash, report("payments", CASH_DAY), "Repeated report read must not duplicate money out");
        money("10000", report("sales", SALE_DAY).path("total"));
        money("0", report("payments", CASH_DAY + "&patient=NO_MATCH").path("refundedTotal"));
    }

    @Test void inactivePaymentAndVoidedReceiptAreExcludedFromBothTotalsAndChannels() {
        positionOriginalCourseSale();
        money("10000", report("payments", CASH_DAY).path("total"));
        db.update("update payment_transaction set payment_status=false where receipt_id=(select receipt_id from course where course_id=?)", courseId);
        var unpaid = report("payments", CASH_DAY);
        assertEquals(0, unpaid.path("rows").size()); money("0", unpaid.path("total"));
        assertEquals(0, unpaid.path("byMethod").size());
        money("10000", report("sales", SALE_DAY).path("total"));
        db.update("update payment_transaction set payment_status=true where receipt_id=(select receipt_id from course where course_id=?)", courseId);
        db.update("update receipt set receipt_status='Voided' where receipt_id=(select receipt_id from course where course_id=?)", courseId);
        assertEquals(0, report("sales", SALE_DAY).path("rows").size());
        var voided = report("payments", CASH_DAY);
        assertEquals(0, voided.path("rows").size()); money("0", voided.path("total"));
        assertEquals(0, voided.path("byMethod").size());
    }
}
