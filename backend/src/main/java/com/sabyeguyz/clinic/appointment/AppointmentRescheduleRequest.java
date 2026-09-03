package com.sabyeguyz.clinic.appointment;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record AppointmentRescheduleRequest(
        @NotNull OffsetDateTime startAt,
        Long therapistId
) {}