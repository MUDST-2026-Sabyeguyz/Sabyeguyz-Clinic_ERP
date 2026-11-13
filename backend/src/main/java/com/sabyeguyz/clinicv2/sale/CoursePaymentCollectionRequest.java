package com.sabyeguyz.clinicv2.sale;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** HTTP inputs describe this receipt only; the server never derives an outstanding course balance. */
public record CoursePaymentCollectionRequest(
        @NotNull @Positive Long patientId,
        @NotBlank String branchCode,
        @NotEmpty List<@NotNull @Valid Item> items,
        String billDiscountType,
        @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal billDiscountValue,
        @NotBlank String paymentLabelCode,
        @NotBlank String paymentMethodCode,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal paymentAmount) {
    public record Item(
            @NotNull @Positive Long courseId,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal amount) {}
}
