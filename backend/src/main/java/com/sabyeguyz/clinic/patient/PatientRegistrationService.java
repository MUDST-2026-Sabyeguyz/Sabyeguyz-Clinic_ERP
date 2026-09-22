package com.sabyeguyz.clinic.patient;

import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.branch.BranchRepository;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
public class PatientRegistrationService {
    private final BranchRepository branchRepository;
    private final PatientRepository patientRepository;
    private final EntityManager entityManager;
    private final HnGenerator hnGenerator;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public PatientRegistrationService(BranchRepository branchRepository, PatientRepository patientRepository, EntityManager entityManager, HnGenerator hnGenerator) {
        this.branchRepository = branchRepository;
        this.patientRepository = patientRepository;
        this.entityManager = entityManager;
        this.hnGenerator = hnGenerator;
    }

    @Transactional
    public PatientResponse register(PatientRegistrationRequest request) {
        Branch branch = branchRepository.findByCodeAndActiveTrue(request.branchCode().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน"));
        if (patientRepository.existsByUsernameOrIdentityNumber(request.username(), request.identityNumber())) {
            throw new IllegalArgumentException("ชื่อผู้ใช้หรือเลขเอกสารยืนยันตัวตนถูกใช้งานแล้ว");
        }
        Patient patient = patientRepository.save(new Patient(branch, request, passwordEncoder.encode(request.password())));
        entityManager.flush();
        patient.assignHn(hnGenerator.next(branch.getId(), branch.getNumericCode(), LocalDate.now()));
        return PatientResponse.from(patient);
    }
}
