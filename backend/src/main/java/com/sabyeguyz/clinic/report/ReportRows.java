package com.sabyeguyz.clinic.report;

import com.sabyeguyz.clinic.course.CoursePurchaseStatus;
import com.sabyeguyz.clinic.sale.SaleItemType;
import java.math.BigDecimal;
import java.time.Instant;

public final class ReportRows {
    private ReportRows() {}

    public record GenderCount(String gender, long patients) {}

    public record CategoryAmount(SaleItemType type, BigDecimal amount) {}

    public record BranchAmount(String branchCode, String branchName, BigDecimal amount) {}

    public record SaleAmount(Instant soldAt, BigDecimal amount) {}

    public record CategorySaleAmount(Instant soldAt, SaleItemType type, BigDecimal amount) {}

    public record StaffSaleRow(Long staffId, String employeeCode, String fullName,
                               long receipts, BigDecimal amount) {}

    public record PurchaseRow(Long purchaseId, Long patientId, String hn, String patientFullName,
                              String branchCode, String courseCode, String courseName,
                              int sessions, int bonusSessions, BigDecimal price,
                              Instant purchasedAt, Instant expiresAt, CoursePurchaseStatus status) {}

    public record PurchaseTotal(Long purchaseId, long value) {}

    public record StatusCount(com.sabyeguyz.clinic.appointment.AppointmentStatus status, long appointments) {}

    public record TherapistCount(Long therapistId, String code, String fullName, long appointments) {}

    public record AppointmentMoment(Instant startAt, com.sabyeguyz.clinic.appointment.AppointmentStatus status) {}
}
