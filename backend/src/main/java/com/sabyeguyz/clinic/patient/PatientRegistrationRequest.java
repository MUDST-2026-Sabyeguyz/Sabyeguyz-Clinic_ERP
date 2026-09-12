package com.sabyeguyz.clinic.patient;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PatientRegistrationRequest(
        @NotBlank @Size(max = 2) String branchCode,
        @NotBlank @Size(max = 80) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 30) String title,
        @NotBlank @Size(max = 160) String fullName,
        @NotNull @Past LocalDate birthDate,
        @Size(max = 30) String gender,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank @Size(max = 40) String identityDocumentType,
        @NotBlank @Size(max = 40) String identityNumber,
        @NotBlank String address,
        boolean drugAllergy,
        boolean foodAllergy,
        String chronicDisease,
        @NotBlank @Size(max = 160) String emergencyContactName,
        @NotBlank @Size(max = 80) String emergencyContactRelationship,
        @NotBlank @Size(max = 20) String emergencyContactPhone,
        @AssertTrue boolean privacyConsent,
        @Size(max = 80) String nickname,
        @Size(max = 30) String bloodGroup,
        @Size(max = 80) String nationality,
        @Size(max = 80) String customerType,
        @Size(max = 80) String customerSource,
        @Size(max = 160) String insuranceCompany
) {
    public PatientRegistrationRequest(String branchCode, String username, String email, String password,
            String title, String fullName, LocalDate birthDate, String gender, String phone,
            String identityDocumentType, String identityNumber, String address, boolean drugAllergy,
            boolean foodAllergy, String chronicDisease, String emergencyContactName,
            String emergencyContactRelationship, String emergencyContactPhone, boolean privacyConsent) {
        this(branchCode, username, email, password, title, fullName, birthDate, gender, phone,
                identityDocumentType, identityNumber, address, drugAllergy, foodAllergy, chronicDisease,
                emergencyContactName, emergencyContactRelationship, emergencyContactPhone, privacyConsent,
                null, null, null, null, null, null);
    }
}
