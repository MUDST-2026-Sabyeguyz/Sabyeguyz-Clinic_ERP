package com.sabyeguyz.clinic.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record AppointmentCreateRequest(
        @NotBlank @Size(max = 2) String branchCode,
        @NotNull Long patientId,
        @NotNull Long therapistId,
        @NotNull Long serviceId,
        @NotNull OffsetDateTime startAt,
        @Size(max = 500) String note
) { }