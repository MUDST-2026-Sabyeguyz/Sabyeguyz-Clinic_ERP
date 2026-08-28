package com.sabyeguyz.clinic.patient;

import java.util.List;
import org.springframework.data.domain.Page;

public record PatientSearchResponse(
        List<PatientResponse> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    static PatientSearchResponse from(Page<Patient> patients) {
        return new PatientSearchResponse(
                patients.getContent().stream().map(PatientResponse::from).toList(),
                patients.getNumber(), patients.getSize(), patients.getTotalElements(), patients.getTotalPages());
    }
}
