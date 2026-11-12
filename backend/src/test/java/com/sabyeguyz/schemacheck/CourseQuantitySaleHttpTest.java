package com.sabyeguyz.schemacheck;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** US043/044/055: cart quantity means distinct course sets, with bounded receipt numbering. */
class CourseQuantitySaleHttpTest extends CoursePaymentHttpTestSupport {
    private Map<String, Object> line(int quantity, BigDecimal discount) {
        long template = db.queryForObject("select service_id from service where service_code='COURSE_10'", Long.class);
        return Map.of("type", "COURSE", "referenceId", template, "quantity", quantity,
                "discountType", "FIXED", "discountValue", discount);
    }

    private HttpResult sell(int quantity, BigDecimal discount) {
        BigDecimal price = db.queryForObject("select service_price from service where service_code='COURSE_10'", BigDecimal.class);
        long seller = db.queryForObject("select staff_id from staff where staff_code='THP-001'", Long.class);
        return reply("POST", "/api/payments", Map.of("patientId", patientId, "branchCode", "01",
                "salespersonStaffId", seller, "paymentLabelCode", "FULL", "items", List.of(line(quantity, discount)),
                "payments", List.of(Map.of("paymentMethodCode", "PromptPay", "amount", price.multiply(BigDecimal.valueOf(quantity)).subtract(discount)))));
    }

    @Test
    void quantityTwoCreatesTwoCoursesWithSuffixedNumbersAndPreservesCartDiscount() {
        long visits = db.queryForObject("select count(*) from patient_visit", Long.class);
        long treatments = db.queryForObject("select count(*) from treatment", Long.class);
        var sold = sell(2, new BigDecimal("100.01"));
        assertEquals(201, sold.status(), sold.body().toString());
        String receipt = sold.body().path("receiptNo").asText();
        var courses = db.queryForList("select c.course_no,c.total_visits,c.remaining_visits,s.line_discount_amount,s.quantity from course c join sales_transaction s on s.transaction_id=c.sale_transaction_id join receipt r on r.receipt_id=c.receipt_id where r.receipt_no=? and r.branch_id=? order by c.course_sequence", receipt, branchId);
        assertEquals(2, courses.size());
        assertEquals(receipt + "-01", courses.get(0).get("course_no"));
        assertEquals(receipt + "-02", courses.get(1).get("course_no"));
        assertEquals(new BigDecimal("50.00"), courses.get(0).get("line_discount_amount"));
        assertEquals(new BigDecimal("50.01"), courses.get(1).get("line_discount_amount"));
        for (var course : courses) {
            assertEquals(1, ((Number) course.get("quantity")).intValue());
            assertEquals(course.get("total_visits"), course.get("remaining_visits"));
        }
        assertEquals(visits, db.queryForObject("select count(*) from patient_visit", Long.class));
        assertEquals(treatments, db.queryForObject("select count(*) from treatment", Long.class));
    }

    @Test
    void aSingleCourseKeepsTheReceiptNumber() {
        var sold = sell(1, BigDecimal.ZERO);
        assertEquals(201, sold.status(), sold.body().toString());
        String receipt = sold.body().path("receiptNo").asText();
        assertEquals(receipt, db.queryForObject("select c.course_no from course c join receipt r using(receipt_id) where r.receipt_no=? and r.branch_id=?", String.class, receipt, branchId));
    }

    @Test
    void rejectsZeroNegativeAndMoreThan99SetsBeforeCreatingAnySale() {
        long receipts = db.queryForObject("select count(*) from receipt", Long.class);
        long courses = db.queryForObject("select count(*) from course", Long.class);
        for (int quantity : List.of(0, -1, 100)) {
            var result = sell(quantity, BigDecimal.ZERO);
            assertEquals(400, result.status(), result.body().toString());
        }
        assertEquals(receipts, db.queryForObject("select count(*) from receipt", Long.class));
        assertEquals(courses, db.queryForObject("select count(*) from course", Long.class));
    }

    @Test
    void exactly99SetsHaveUniqueNumbersAndOnePayment() {
        var sold = sell(99, BigDecimal.ZERO);
        assertEquals(201, sold.status(), sold.body().toString());
        String receipt = sold.body().path("receiptNo").asText();
        assertEquals(99, db.queryForObject("select count(*) from course c join receipt r using(receipt_id) where r.receipt_no=? and r.branch_id=?", Integer.class, receipt, branchId));
        assertEquals(1, db.queryForObject("select count(*) from payment_transaction p join receipt r using(receipt_id) where r.receipt_no=? and r.branch_id=?", Integer.class, receipt, branchId));
        assertEquals(receipt + "-99", db.queryForObject("select c.course_no from course c join receipt r using(receipt_id) where r.receipt_no=? and r.branch_id=? and c.course_sequence=99", String.class, receipt, branchId));
    }
}
