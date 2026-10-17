package com.sabyeguyz.clinic.patient;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private final PatientRepository patientRepository;

    public PatientQueryService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional(readOnly = true)
    public PatientSearchResponse search(String query, String branchCode, String sort, int page, int size) {
        validatePagination(page, size);
        String normalizedQuery = query == null ? "" : query.trim();
        Sort sorting = switch (sort) {
            case "nameDesc" -> Sort.by("fullName").descending().and(Sort.by("id").descending());
            case "newest" -> Sort.by("id").descending();
            default -> Sort.by("fullName").ascending().and(Sort.by("id").ascending());
        };
        return PatientSearchResponse.from(patientRepository.search(
                normalizedQuery, branchCode == null || branchCode.isBlank() ? null : branchCode.trim().toUpperCase(),
                PageRequest.of(page, size, sorting)));
    }

    @Transactional(readOnly = true)
    public PatientProfileResponse getProfile(long id) {
        return patientRepository.findById(id)
                .map(PatientProfileResponse::from)
                .orElseThrow(() -> new PatientNotFoundException(id));
    }

    @Transactional
    public PatientProfileResponse update(long id, PatientUpdateRequest request) {
        Patient patient = patientRepository.findById(id).orElseThrow(() -> new PatientNotFoundException(id));
        if (patientRepository.existsByIdentityNumberAndIdNot(request.identityNumber().trim(), id))
            throw new IllegalArgumentException("เลขเอกสารยืนยันตัวตนซ้ำ");
        if (request.email() != null && patientRepository.existsByEmailAndIdNot(request.email().trim(), id))
            throw new IllegalArgumentException("อีเมลซ้ำ");
        patient.update(request);
        return PatientProfileResponse.from(patientRepository.save(patient));
    }

    private void validatePagination(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page ต้องไม่น้อยกว่า 0");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size ต้องอยู่ระหว่าง 1 ถึง " + MAX_PAGE_SIZE);
        }
    }
}
