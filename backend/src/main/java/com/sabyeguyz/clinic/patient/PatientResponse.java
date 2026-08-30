package com.sabyeguyz.clinic.patient;

public record PatientResponse(Long id, String hn, String fullName, String phone, boolean active) {
    static PatientResponse from(Patient patient) {
        return new PatientResponse(patient.getId(), patient.getHn(), patient.getFullName(), patient.getPhone(), patient.isActive());
    }
}
