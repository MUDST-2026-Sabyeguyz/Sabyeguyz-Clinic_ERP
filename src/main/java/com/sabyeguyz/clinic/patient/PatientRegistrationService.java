package com.sabyeguyz.clinic.patient;

import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.branch.BranchRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientRegistrationService {
    private final BranchRepository branchRepository;
    private final PatientRepository patientRepository;
    private final EntityManager entityManager;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PatientRegistrationService(BranchRepository branchRepository, PatientRepository patientRepository, EntityManager entityManager) {
        this.branchRepository = branchRepository;
        this.patientRepository = patientRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public PatientResponse register(PatientRegistrationRequest request) {
        Branch branch = branchRepository.findByCodeAndActiveTrue(request.branchCode().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน"));
        if (patientRepository.existsByUsernameOrEmailOrIdentityNumber(request.username(), request.email(), request.identityNumber())) {
            throw new IllegalArgumentException("ชื่อผู้ใช้ อีเมล หรือเลขเอกสารยืนยันตัวตนถูกใช้งานแล้ว");
        }
        Patient patient = patientRepository.save(new Patient(branch, request, passwordEncoder.encode(request.password())));
        entityManager.flush();
        patient.assignHn();
        return PatientResponse.from(patient);
    }
}
