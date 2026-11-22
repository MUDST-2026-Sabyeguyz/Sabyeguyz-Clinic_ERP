package com.sabyeguyz.schemacheck;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReportTransactionsHttpTest extends CoursePaymentHttpTestSupport {
    private static final String PERIOD = "?from=2034-02-01&to=2034-02-01&branchCode=01";
    private HttpResult collect(String key, String amount) {
        var result = replyWithHeaders(token, "POST", "/api/course-payment-collections", Map.of(
                "patientId", patientId, "branchCode", "01", "items", List.of(Map.of("courseId", courseId,
                "amount", new BigDecimal(amount))), "paymentLabelCode", "INSTALLMENT",
                "paymentMethodCode", "PromptPay", "paymentAmount", new BigDecimal(amount)),
                Map.of("Idempotency-Key", key));
        assertEquals(201, result.status(), result.body().toString());
        db.update("update payment_transaction set payment_date='2034-02-01T01:00:00Z' where payment_id=?",
                result.body().path("paymentId").asLong());
        return result;
    }
    @Test void collectionsAppearOnPaymentDateButNeverCreateNewSales() {
        var collection = collect("report-collection", "15.55");
        var cash = reply("GET", "/api/reports/payments-detail" + PERIOD, null);
        assertEquals(200, cash.status(), cash.body().toString());
        assertEquals("PAYMENT_DATE", cash.body().path("basis").asText());
        assertEquals(1, cash.body().path("rows").size());
        assertEquals("COURSE_COLLECTION", cash.body().path("rows").get(0).path("source").asText());
        assertEquals(collection.body().path("receiptId").asLong(), cash.body().path("rows").get(0).path("receiptId").asLong());
        assertEquals(0, new BigDecimal("15.55").compareTo(cash.body().path("total").decimalValue()));
        assertEquals(0, reply("GET", "/api/reports/sales-detail" + PERIOD, null).body().path("rows").size());
    }
    @Test void returnsAllRowsAndTotalsRegardlessOfScreenPaginationAndFiltersEmptyExactly() {
        for (int i = 0; i < 13; i++) collect("report-page-" + i, "10.00");
        var cash = reply("GET", "/api/reports/payments-detail" + PERIOD, null);
        assertEquals(200, cash.status(), cash.body().toString());
        assertEquals(13, cash.body().path("rows").size());
        assertEquals(0, new BigDecimal("130.00").compareTo(cash.body().path("total").decimalValue()));
        assertEquals(0, reply("GET", "/api/reports/payments-detail" + PERIOD + "&patient=does-not-exist", null).body().path("rows").size());
        assertEquals(0, reply("GET", "/api/reports/payments-detail" + PERIOD + "&saleType=SingleVisit", null).body().path("rows").size());
    }
    @Test void voidedCollectionIsNotCountedAsSuccessfulCash() {
        var collection = collect("report-void", "20");
        db.update("update payment_transaction set payment_status=false where payment_id=?", collection.body().path("paymentId").asLong());
        assertEquals(0, reply("GET", "/api/reports/payments-detail" + PERIOD, null).body().path("rows").size());
    }
    @Test void directReportCallsRequireManagerAndValidRange() {
        String therapist = login("therapist", "therapist123");
        for (String path : List.of("/api/reports/sales-detail", "/api/reports/payments-detail")) {
            assertEquals(401, replyAs(null, "GET", path + PERIOD, null).status());
            assertEquals(403, replyAs(therapist, "GET", path + PERIOD, null).status());
            assertEquals(200, reply("GET", path + PERIOD, null).status());
            assertEquals(400, reply("GET", path + "?from=2034-02-02&to=2034-02-01", null).status());
        }
    }
}
