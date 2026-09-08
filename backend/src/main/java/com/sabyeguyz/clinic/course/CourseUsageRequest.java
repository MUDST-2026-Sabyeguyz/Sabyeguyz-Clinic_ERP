package com.sabyeguyz.clinic.course;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CourseUsageRequest(
        @NotNull Long coursePurchaseId,
        Long appointmentId,
        @Size(max = 500) String note
) {}
