package com.sabyeguyz.clinic.course;
import jakarta.validation.constraints.*;
public record CourseTransferRequest(@NotNull @Positive Long sourcePurchaseId,
        @NotNull @Positive Long destinationPatientId, @Positive int quantity,
        @NotBlank @Size(max = 1000) String reason) {}
