package com.sabyeguyz.clinic.treatment;
import jakarta.validation.constraints.*;
public record TreatmentRequest(@NotNull @Positive Long patientId, @Positive Long appointmentId,
        @Positive Long staffId, @NotBlank @Size(max=120) String service, @NotNull String note) {}
