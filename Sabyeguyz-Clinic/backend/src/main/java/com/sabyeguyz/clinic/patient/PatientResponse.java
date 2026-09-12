package com.sabyeguyz.clinic.patient;

public record PatientResponse(Long id, String hn, String fullName, String phone, boolean active,
                              Long branchId, String branchCode, String branchName) {
    public PatientResponse(Long id, String hn, String fullName, String phone, boolean active) {
        this(id, hn, fullName, phone, active, null, null, null);
    }

    static PatientResponse from(Patient patient) {
        var branch = patient.getBranch();
        return new PatientResponse(patient.getId(), patient.getHn(), patient.getFullName(), patient.getPhone(),
                patient.isActive(), branch.getId(), branch.getCode(), branch.getName());
    }
}
