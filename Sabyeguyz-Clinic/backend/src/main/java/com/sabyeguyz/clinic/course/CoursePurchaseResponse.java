package com.sabyeguyz.clinic.course;

import java.math.BigDecimal;
import java.time.Instant;

public record CoursePurchaseResponse(
        Long id,
        PatientSummary patient,
        String branchCode,
        CourseSummary course,
        BigDecimal price,
        int sessions,
        int bonusSessions,
        /** จำนวนครั้งที่ซื้อทั้งหมด รวม Bonus */
        int purchased,
        int used,
        int remaining,
        Instant purchasedAt,
        Instant expiresAt,
        boolean expired,
        /** ตัดสิทธิ์ได้หรือไม่ ณ ตอนนี้ */
        boolean usable,
        CoursePurchaseStatus status, String commissionStatus, BigDecimal lockedCommissionRate,
        BigDecimal totalCourseCommissionPool, BigDecimal commissionAllocationPerVisit
) {
    public record PatientSummary(Long id, String hn, String fullName) {}

    public record CourseSummary(Long id, String code, String name) {}

    public static CoursePurchaseResponse from(CoursePurchase purchase, int used, int remaining, Instant now) {
        boolean expired = purchase.isExpiredAt(now);
        boolean usable = !expired
                && purchase.getStatus() == CoursePurchaseStatus.ACTIVE
                && remaining > 0;
        return new CoursePurchaseResponse(
                purchase.getId(),
                new PatientSummary(purchase.getPatient().getId(), purchase.getPatient().getHn(),
                        purchase.getPatient().getFullName()),
                purchase.getBranch().getCode(),
                new CourseSummary(purchase.getCourse().getId(), purchase.getCourseCode(), purchase.getCourseName()),
                purchase.getPrice(),
                purchase.getSessions(),
                purchase.getBonusSessions(),
                purchase.totalSessions(),
                used,
                remaining,
                purchase.getPurchasedAt(),
                purchase.getExpiresAt(),
                expired,
                usable,
                purchase.getStatus(), purchase.getCommissionStatus(), purchase.getLockedCommissionRate(),
                purchase.getTotalCourseCommissionPool(), purchase.getCommissionAllocationPerVisit());
    }
}
