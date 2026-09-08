package com.sabyeguyz.clinic.sale;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * คำขอเปิดใบเสร็จ ไม่มีช่องให้ส่งราคาเข้ามา ราคาถูกอ่านจาก Service/Course master เสมอ
 * ตามข้อกำหนด "คำนวณยอดจาก Service / Course"
 */
public record SaleRequest(
        @NotNull Long patientId,
        @NotBlank @Size(max = 2) String branchCode,
        /** พนักงานขาย ไม่บังคับ และไม่จำเป็นต้องเป็นคนเดียวกับผู้รักษา */
        Long salespersonStaffId,
        @Size(max = 500) String note,
        @NotEmpty @Valid List<Item> items,
        @NotEmpty @Valid List<Payment> payments
) {
    /**
     * @param referenceId id ของ service สำหรับ ASSESSMENT/SINGLE_VISIT หรือ id ของ course สำหรับ COURSE
     */
    public record Item(
            @NotNull SaleItemType type,
            @NotNull Long referenceId,
            @Min(1) int quantity
    ) {}

    public record Payment(
            @NotBlank String paymentMethodCode,
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount
    ) {}
}
