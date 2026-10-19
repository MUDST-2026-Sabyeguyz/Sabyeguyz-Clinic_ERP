package com.sabyeguyz.clinicv2.patient;

import java.time.Instant;
import java.time.LocalDate;
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
        patient.setTitle(normalizeTitle(requireText(input.title(), "title"), input.customerType()));
        patient.setPatientFname(requireText(input.patientFname(), "patientFname"));
        patient.setPatientLname(requireText(input.patientLname(), "patientLname"));
        patient.setPatientMiddleName(normalizeMiddleName(input.middleName()));
        patient.setGender(require(input.gender(), "gender"));
        // Thai patients must give a phone number and a 13-digit national id; foreigners may give neither
        // (they rarely carry documents), so only a provided national id is format-checked for them.
        boolean thai = input.customerType() == com.sabyeguyz.clinicv2.enums.CustomerType.Thai;
        String phone = input.phone() == null || input.phone().isBlank() ? null : input.phone().trim();
        String nationalId = input.nationalId() == null || input.nationalId().isBlank() ? null : input.nationalId().trim();
        if (thai && phone == null) {
            throw new IllegalArgumentException("phone ห้ามว่าง");
        }
        if (thai && !phone.matches("[0-9]{10}")) {
            throw new IllegalArgumentException("เบอร์โทรต้องเป็นตัวเลข 10 หลัก");
        }
        if (phone != null && !phone.matches("[0-9]{1,10}")) {
            throw new IllegalArgumentException("เบอร์โทรต้องเป็นตัวเลขไม่เกิน 10 หลัก");
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

        // patient_hn is NOT NULL, so the HN is issued before the insert. Both writes share this
        // transaction: if the insert fails, the sequence increment rolls back with it.
        patient.setPatientHn(hnGenerator.next(input.branch(), now.atZone(java.time.ZoneOffset.UTC).toLocalDate()));
        return repository.save(patient);
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

    /**
     * Edits a registered patient. A null argument leaves that field as it is; identity numbers, phone and names
     * follow the same rules as registration (a Thai patient keeps a phone number and a 13-digit national id).
     * middleName is changed, including cleared to null, only when updateMiddleName is true.
     */
    @Transactional
    public Patient updateDetails(Long id, String title, String fname, String lname,
                                  com.sabyeguyz.clinicv2.enums.Gender gender, String phone, String nickname,
                                  String nationalId, String passportNo, LocalDate birthdate,
                                  com.sabyeguyz.clinicv2.enums.BloodGroup bloodGroup, String nationality,
                                  String address, String middleName, boolean updateMiddleName, Long actorId) {
        Patient patient = find(id);
        if (title != null && !title.isBlank()) patient.setTitle(normalizeTitle(title, patient.getCustomerType()));
        if (updateMiddleName) patient.setPatientMiddleName(normalizeMiddleName(middleName));
        if (fname != null && !fname.isBlank()) patient.setPatientFname(fname.trim());
        if (lname != null && !lname.isBlank()) patient.setPatientLname(lname.trim());
        if (gender != null) patient.setGender(gender);
        if (phone != null) patient.setPhone(phone.isBlank() ? null : phone.trim());
        if (nickname != null) patient.setPatientNickname(nickname.isBlank() ? null : nickname.trim());
        if (nationalId != null && !nationalId.isBlank()) {
            if (!nationalId.trim().matches("[0-9]{13}")) {
                throw new IllegalArgumentException("เลขบัตรประชาชนต้องเป็นตัวเลข 13 หลัก");
            }
            patient.setNationalId(nationalId.trim());
        }
        if (passportNo != null && !passportNo.isBlank()) patient.setPassportNo(passportNo.trim());
        if (birthdate != null) patient.setBirthdate(birthdate);
        if (bloodGroup != null) patient.setBloodGroup(bloodGroup);
        if (nationality != null && !nationality.isBlank()) patient.setNationality(nationality.trim());
        if (address != null) patient.setAddress(address);
        if (patient.getCustomerType() == com.sabyeguyz.clinicv2.enums.CustomerType.Thai) {
            if (patient.getPhone() == null) throw new IllegalArgumentException("phone ห้ามว่าง");
            if (patient.getNationalId() == null) {
                throw new IllegalArgumentException("ผู้ป่วยชาวไทยต้องระบุเลขบัตรประชาชน");
            }
        }
        if (patient.getPhone() != null) {
            boolean thai = patient.getCustomerType() == com.sabyeguyz.clinicv2.enums.CustomerType.Thai;
            String pattern = thai ? "[0-9]{10}" : "[0-9]{1,10}";
            if (!patient.getPhone().matches(pattern)) {
                throw new IllegalArgumentException(thai
                        ? "เบอร์โทรต้องเป็นตัวเลข 10 หลัก"
                        : "เบอร์โทรต้องเป็นตัวเลขไม่เกิน 10 หลัก");
            }
        }
        patient.setPatientUpdateAt(Instant.now());
        patient.setPatientUpdateBy(actorId);
        return repository.save(patient);
    }

    private Patient find(Long id) {
        return repository.findById(id).orElseThrow(PatientNotFoundException::new);
    }

    private static String normalizeMiddleName(String value) {
        if (value == null || value.isBlank()) return null;
        String name = value.trim();
        if (name.length() > 100) throw new IllegalArgumentException("ชื่อกลางต้องไม่เกิน 100 ตัวอักษร");
        return name;
    }

    private static String normalizeTitle(String value, com.sabyeguyz.clinicv2.enums.CustomerType type) {
        int title = switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "นาย", "mr.", "mr" -> 0;
            case "นางสาว", "miss" -> 1;
            case "นาง", "mrs.", "mrs" -> 2;
            case "เด็กชาย", "boy" -> 3;
            case "เด็กหญิง", "girl" -> 4;
            default -> throw new IllegalArgumentException("คำนำหน้าชื่อไม่ถูกต้อง");
        };
        String[] labels = type == com.sabyeguyz.clinicv2.enums.CustomerType.Foreigner
                ? new String[] {"Mr.", "Miss", "Mrs.", "Boy", "Girl"}
                : new String[] {"นาย", "นางสาว", "นาง", "เด็กชาย", "เด็กหญิง"};
        return labels[title];
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
