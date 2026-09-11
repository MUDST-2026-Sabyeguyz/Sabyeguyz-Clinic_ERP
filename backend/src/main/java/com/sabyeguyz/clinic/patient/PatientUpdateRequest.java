package com.sabyeguyz.clinic.patient;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PatientUpdateRequest(String title, @NotBlank String fullName, @NotNull LocalDate birthDate,
        String gender, @NotBlank String phone, @NotBlank String identityDocumentType, @NotBlank String identityNumber,
        @NotBlank String address, boolean drugAllergy, boolean foodAllergy, String chronicDisease,
        @NotBlank String emergencyContactName, @NotBlank String emergencyContactRelationship,
        @NotBlank String emergencyContactPhone, boolean privacyConsent, boolean active) {}
