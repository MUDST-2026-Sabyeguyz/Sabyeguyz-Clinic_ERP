package com.sabyeguyz.clinic.report;

import com.sabyeguyz.clinic.course.CoursePurchaseStatus;
import com.sabyeguyz.clinic.sale.SaleItemType;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * แถวดิบที่ได้จาก aggregate query ก่อนจะประกอบเป็น response
 * ต้องเป็น public เพราะ Hibernate สร้าง record พวกนี้ผ่าน constructor expression ของ JPQL
 */
public final class ReportRows {
    private ReportRows() {}

    public record GenderCount(String gender, long patients) {}

    public record CategoryAmount(SaleItemType type, BigDecimal amount) {}

    public record BranchAmount(String branchCode, String branchName, BigDecimal amount) {}

    /** ยอดของใบเสร็จหนึ่งใบ ใช้จัดกลุ่มเป็นรายวันในโซนเวลาคลินิกฝั่ง Java */
    public record SaleAmount(Instant soldAt, BigDecimal amount) {}

    public record StaffSaleRow(Long staffId, String employeeCode, String fullName,
                               long receipts, BigDecimal amount) {}

    public record PurchaseRow(Long purchaseId, Long patientId, String hn, String patientFullName,
                              String branchCode, String courseCode, String courseName,
                              int sessions, int bonusSessions, BigDecimal price,
                              Instant purchasedAt, Instant expiresAt, CoursePurchaseStatus status) {}

    public record PurchaseTotal(Long purchaseId, long value) {}
}
