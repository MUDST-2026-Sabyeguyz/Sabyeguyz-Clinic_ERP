package com.sabyeguyz.clinic.patient;

import java.time.LocalDate;

public record PatientProfileResponse(
        Long id,
        String hn,
        BranchSummary branch,
        String title,
        String fullName,
        LocalDate birthDate,
        String gender,
        String phone,
        String identityDocumentType,
        String identityNumber,
        String address,
        boolean drugAllergy,
        boolean foodAllergy,
        String chronicDisease,
        String emergencyContactName,
        String emergencyContactRelationship,
        String emergencyContactPhone,
        boolean privacyConsent,
        boolean active
) {
    public record BranchSummary(Long id, String code, String name, boolean active) {}

    static PatientProfileResponse from(Patient patient) {
        var branch = patient.getBranch();
        return new PatientProfileResponse(patient.getId(), patient.getHn(),
                new BranchSummary(branch.getId(), branch.getCode(), branch.getName(), branch.isActive()),
                patient.getTitle(), patient.getFullName(), patient.getBirthDate(), patient.getGender(), patient.getPhone(),
                patient.getIdentityDocumentType(), patient.getIdentityNumber(), patient.getAddress(), patient.isDrugAllergy(),
                patient.isFoodAllergy(), patient.getChronicDisease(), patient.getEmergencyContactName(),
                patient.getEmergencyContactRelationship(), patient.getEmergencyContactPhone(), patient.isPrivacyConsent(),
                patient.isActive());
    }
}
