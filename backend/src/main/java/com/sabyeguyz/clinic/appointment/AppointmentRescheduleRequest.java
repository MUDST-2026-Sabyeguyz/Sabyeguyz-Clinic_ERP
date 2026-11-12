package com.sabyeguyz.clinic.appointment;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

public record AppointmentRescheduleRequest(
        @NotNull OffsetDateTime startAt,
        Long therapistId) {
}
