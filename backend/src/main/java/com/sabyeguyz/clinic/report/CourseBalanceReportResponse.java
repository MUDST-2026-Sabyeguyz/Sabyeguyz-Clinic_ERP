package com.sabyeguyz.clinic.report;

import com.sabyeguyz.clinic.course.CoursePurchaseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Course Balance Report ตาม Workflow Brief V1 §8 — คอร์สของผู้ป่วยแต่ละราย ซื้อ ใช้ และคงเหลือกี่ครั้ง
 *
 * <p>คอลัมน์ "โอน" ที่ brief ขอไว้ยังไม่มี เพราะ Course Transfer ยังไม่เข้า develop</p>
 */
public record CourseBalanceReportResponse(
        LocalDate from,
        LocalDate to,
        String branchCode,
        int purchases,
        long totalPurchasedSessions,
        long totalUsedSessions,
        long totalRemainingSessions,
        BigDecimal totalValue,
        List<Row> rows
) {
    public record Row(
            Long coursePurchaseId,
            Long patientId,
            String hn,
            String patientFullName,
            String branchCode,
            String courseCode,
            String courseName,
            BigDecimal price,
            int purchased,
            int used,
            int remaining,
            Instant purchasedAt,
            Instant expiresAt,
            boolean expired,
            CoursePurchaseStatus status
    ) {}
}
