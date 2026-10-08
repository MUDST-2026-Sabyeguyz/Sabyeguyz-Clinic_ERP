package com.sabyeguyz.clinicv2.patient;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Patient registration (Clinic ERP Workflow Brief §3). */
@Service
public class PatientService {
    private final PatientRepository repository;
    private final HnGeneratorService hnGenerator;

    public PatientService(PatientRepository repository, HnGeneratorService hnGenerator) {
        this.repository = repository;
        this.hnGenerator = hnGenerator;
    }

    @Transactional(readOnly = true)
    public List<Patient> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Patient get(Long id) {
        return find(id);
    }

    @Transactional
    public Patient register(PatientRegistration input, Long actorId) {
        if (input.branch() == null) {
            throw new IllegalArgumentException("ต้องระบุสาขาที่ลงทะเบียน");
        }
        Patient patient = new Patient();
        patient.setBranch(input.branch());
        patient.setCustomerType(require(input.customerType(), "customerType"));
        patient.setTitle(requireText(input.title(), "title"));
        patient.setPatientFname(requireText(input.patientFname(), "patientFname"));
        patient.setPatientLname(requireText(input.patientLname(), "patientLname"));
        patient.setGender(require(input.gender(), "gender"));
        // Thai patients must give a phone number and a 13-digit national id; foreigners may give neither
        // (they rarely carry documents), so only a provided national id is format-checked for them.
        boolean thai = input.customerType() == com.sabyeguyz.clinicv2.enums.CustomerType.Thai;
        String phone = input.phone() == null || input.phone().isBlank() ? null : input.phone().trim();
        String nationalId = input.nationalId() == null || input.nationalId().isBlank() ? null : input.nationalId().trim();
        if (thai && phone == null) {
            throw new IllegalArgumentException("phone ห้ามว่าง");
        }
        if (thai && nationalId == null) {
            throw new IllegalArgumentException("ผู้ป่วยชาวไทยต้องระบุเลขบัตรประชาชน");
        }
        if (nationalId != null && !nationalId.matches("[0-9]{13}")) {
            throw new IllegalArgumentException("เลขบัตรประชาชนต้องเป็นตัวเลข 13 หลัก");
        }
        patient.setPhone(phone);
        patient.setGenderDetail(input.genderDetail());
        patient.setPatientNickname(input.patientNickname());
        patient.setNationalId(nationalId);
        patient.setPassportNo(input.passportNo());
        patient.setBirthdate(input.birthdate());
        patient.setBloodGroup(input.bloodGroup());
        patient.setNationality(input.nationality());
        patient.setAddress(input.address());
        patient.setCustomerGroup(input.customerGroup());
        patient.setReferralChannel(input.referralChannel());
        patient.setInsuranceType(input.insuranceType());
        patient.setInsuranceCompany(input.insuranceCompany());

        Instant now = Instant.now();
        patient.setRegisteredAt(now);
        patient.setRegisteredBy(actorId);

        // Persist first so the HN sequence's running number and this patient's
        // creation are committed together — the HN is never generated for a
        // patient row that could still roll back.
        Patient saved = repository.save(patient);
        saved.setPatientHn(hnGenerator.next(input.branch(), now.atZone(java.time.ZoneOffset.UTC).toLocalDate()));
        return repository.save(saved);
    }

    /** Profile fields only — branchId and patientHn never change after registration. */
    @Transactional
    public Patient updateProfile(Long id, String patientNickname, String phone, String address,
                                  com.sabyeguyz.clinicv2.enums.BloodGroup bloodGroup, String nationality, Long actorId) {
        Patient patient = find(id);
        patient.setPatientNickname(patientNickname);
        patient.setPhone(requireText(phone, "phone"));
        patient.setAddress(address);
        patient.setBloodGroup(bloodGroup);
        patient.setNationality(nationality);
        patient.setPatientUpdateAt(Instant.now());
        patient.setPatientUpdateBy(actorId);
        return repository.save(patient);
    }

    private Patient find(Long id) {
        return repository.findById(id).orElseThrow(PatientNotFoundException::new);
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }
        return value.trim();
    }
}
