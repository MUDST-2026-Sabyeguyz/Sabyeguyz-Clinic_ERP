package com.sabyeguyz.clinic.course;

import java.time.Instant;

/** Read-only transaction view of a course usage and its optional reversal. */
public record CourseUsageTransactionResponse(
        Long id,
        Long coursePurchaseId,
        String patientName,
        String patientHn,
        String courseName,
        String branchCode,
        Instant usedAt,
        Instant voidedAt,
        String voidReason
) {
    static CourseUsageTransactionResponse from(CourseMovement usage, CourseMovement reversal) {
        CoursePurchase purchase = usage.getCoursePurchase();
        return new CourseUsageTransactionResponse(
                usage.getId(),
                purchase.getId(),
                purchase.getPatient().getFullName(),
                purchase.getPatient().getHn(),
                purchase.getCourseName(),
                purchase.getBranch().getBranchCode(),
                usage.getCreatedAt(),
                reversal == null ? null : reversal.getCreatedAt(),
                reversal == null ? null : reversal.getReason());
    }
}
