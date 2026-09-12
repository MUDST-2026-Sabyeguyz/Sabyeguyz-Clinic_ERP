package com.sabyeguyz.clinic.appointment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AppointmentCancelRequest(
        @NotBlank @Size(max = 500) String reason
) {}