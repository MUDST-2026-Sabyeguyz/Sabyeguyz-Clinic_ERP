package com.sabyeguyz.clinicv2.patient;

import org.springframework.transaction.annotation.Transactional;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.enums.BloodGroup;
import com.sabyeguyz.clinicv2.enums.CustomerType;
import com.sabyeguyz.clinicv2.enums.Gender;
import com.sabyeguyz.clinicv2.masterdata.MasterDataValue;
import com.sabyeguyz.clinicv2.masterdata.MasterDataValueRepository;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** รับและแปลงคำขอ API สำหรับค้นหา ลงทะเบียน และแก้ไขโปรไฟล์ผู้ป่วย */
@Transactional
@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private final PatientService service;
    private final PatientRepository repository;
    private final BranchRepository branches;
    private final MasterDataValueRepository masterDataValues;
    private final CurrentActor actor;

    public PatientController(PatientService service, PatientRepository repository, BranchRepository branches,
                              MasterDataValueRepository masterDataValues, CurrentActor actor) {
        this.service = service;
        this.repository = repository;
        this.branches = branches;
        this.masterDataValues = masterDataValues;
        this.actor = actor;
    }

    public record RegisterRequest(
            String branchCode, String customerType, String title, String firstName, String lastName,
            String gender, String phone, String genderDetail, String nickname, String identityDocumentType,
            String identityNumber, LocalDate birthDate, String bloodGroup, String nationality, String address,
            String fullName, com.fasterxml.jackson.databind.JsonNode middleName, String email,
            String customerSource, String insuranceCompany, Boolean drugAllergy, Boolean foodAllergy,
            String chronicDisease, String emergencyContactName, String emergencyContactRelationship,
            String emergencyContactPhone, Boolean privacyConsent) {}

    public record PageResponse<T>(java.util.List<T> items, int page, int size, long totalItems, int totalPages) {}

    public record PatientSummary(Long id, String hn, String fullName, String phone, boolean active,
                                  Long branchId, String branchCode, String branchName) {}

    public record BranchSummary(Long id, String code, String name, boolean active) {}

    public record PatientProfile(
            Long id, String hn, String fullName, String phone, boolean active, BranchSummary branch,
            String title, LocalDate birthDate, String gender, String identityDocumentType, String identityNumber,
            String address, boolean drugAllergy, boolean foodAllergy, String chronicDisease,
            String emergencyContactName, String emergencyContactRelationship, String emergencyContactPhone,
            boolean privacyConsent, String nickname, String bloodGroup, String nationality,
            String customerType, String customerSource, String insuranceCompany, String customerTypeCode,
            String firstName, String lastName, String middleName, String email) {}

    public record IdentityPatientSummary(Long id, String hn, String fullName) {}
    public record IdentityLookupResponse(boolean exists, IdentityPatientSummary patient) {}

    @GetMapping("/identity-lookup")
    public IdentityLookupResponse identityLookup(@RequestParam String nationalId) {
        actor.requireId();
        if (!nationalId.matches("[0-9]{13}")) {
            throw new IllegalArgumentException("เลขบัตรประชาชนต้องเป็นตัวเลข 13 หลัก");
        }
        return repository.findByNationalId(nationalId)
                .map(patient -> new IdentityLookupResponse(true,
                        new IdentityPatientSummary(patient.getId(), patient.getPatientHn(), fullName(patient))))
                .orElseGet(() -> new IdentityLookupResponse(false, null));
    }

    @GetMapping
    public PageResponse<PatientSummary> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String branchCode,
            @RequestParam(defaultValue = "nameAsc") String sort) {
        if (page < 0) throw new IllegalArgumentException("page ต้องไม่น้อยกว่า 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size ต้องอยู่ระหว่าง 1 ถึง 100");
        Long branchId = branchCode == null || branchCode.isBlank() ? null
                : branches.findByBranchCode(branchCode.trim())
                        .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + branchCode)).getId();
        Sort sortOrder = switch (sort) {
            case "newest" -> Sort.by("registeredAt").descending().and(Sort.by("id").descending());
            case "nameDesc" -> Sort.by("patientFname").descending().and(Sort.by("patientLname").descending());
            default -> Sort.by("patientFname").ascending().and(Sort.by("patientLname").ascending());
        };

        Page<Patient> result = repository.search(query, branchId, PageRequest.of(page, size, sortOrder));
        return new PageResponse<>(result.getContent().stream().map(PatientController::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public PatientProfile get(@PathVariable Long id) {
        return toProfile(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientSummary register(@RequestBody RegisterRequest request) {
        Branch branch = branches.findByBranchCode(requireText(request.branchCode(), "branchCode"))
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + request.branchCode()));
        String middleName = middleNameOf(request);
        if (middleName != null && !middleName.isBlank()
                && (request.firstName() == null || request.firstName().isBlank()
                    || request.lastName() == null || request.lastName().isBlank())) {
            throw new IllegalArgumentException("กรุณาส่ง firstName และ lastName แยกกันเมื่อระบุชื่อกลาง");
        }
        String[] names = namesOf(request);
        boolean passport = "PASSPORT".equalsIgnoreCase(request.identityDocumentType());
        PatientRegistration registration = new PatientRegistration(
                branch,
                passport ? CustomerType.Foreigner : parseCustomerType(request.customerType()),
                request.title(),
                names[0],
                names[1],
                parseGender(request.gender()),
                request.phone(),
                request.genderDetail(),
                request.nickname(),
                "PASSPORT".equalsIgnoreCase(request.identityDocumentType()) ? null : request.identityNumber(),
                "PASSPORT".equalsIgnoreCase(request.identityDocumentType()) ? request.identityNumber() : null,
                request.birthDate(),
                parseBloodGroup(request.bloodGroup()),
                request.nationality(),
                request.address(),
                null, resolveMasterDataValue("Referral Channel", request.customerSource()), null,
                resolveMasterDataValue("Insurance Company", request.insuranceCompany()), middleName,
                Boolean.TRUE.equals(request.drugAllergy()), Boolean.TRUE.equals(request.foodAllergy()),
                request.chronicDisease(), request.emergencyContactName(), request.emergencyContactRelationship(),
                request.emergencyContactPhone(), Boolean.TRUE.equals(request.privacyConsent()));
        Patient patient = service.register(registration, actor.currentId());
        patient.setEmail(validEmail(request.email()));
        return toSummary(patient);
    }

    @PutMapping("/{id}")
    public PatientProfile update(@PathVariable Long id, @RequestBody RegisterRequest request) {
        String[] names = namesOf(request);
        String middleName = middleNameOf(request);
        if ((request.firstName() == null || request.firstName().isBlank())
                && (request.lastName() == null || request.lastName().isBlank())
                && request.fullName() != null && !request.fullName().isBlank()) {
            Patient existing = service.get(id);
            if (request.fullName().trim().equals(fullName(existing))) {
                // An unchanged legacy display name must not be split and duplicate the stored middle name.
                names = new String[] {null, null};
            } else if (existing.getPatientMiddleName() != null || (middleName != null && !middleName.isBlank())) {
                throw new IllegalArgumentException("กรุณาส่ง firstName และ lastName แยกกันเมื่อแก้ชื่อผู้ป่วยที่มีชื่อกลาง");
            }
        }
        boolean passport = "PASSPORT".equalsIgnoreCase(request.identityDocumentType());
        Patient patient = service.updateDetails(id, request.title(), names[0], names[1],
                request.gender() == null || request.gender().isBlank() ? null : parseGender(request.gender()),
                request.phone(), request.nickname(), passport ? null : request.identityNumber(),
                passport ? request.identityNumber() : null, request.birthDate(),
                parseBloodGroup(request.bloodGroup()), request.nationality(), request.address(),
                middleName, request.middleName() != null, actor.currentId());
        patient = service.updateMedicalProfile(id, request.drugAllergy(), request.foodAllergy(), request.chronicDisease(),
                request.emergencyContactName(), request.emergencyContactRelationship(), request.emergencyContactPhone(),
                request.privacyConsent(), actor.currentId());
        if (request.customerSource() != null) {
            patient.setReferralChannel(resolveMasterDataValue("Referral Channel", request.customerSource()));
        }
        if (request.insuranceCompany() != null) {
            patient.setInsuranceCompany(resolveMasterDataValue("Insurance Company", request.insuranceCompany()));
        }
        if (request.email() != null) patient.setEmail(validEmail(request.email()));
        return toProfile(patient);
    }

    /** firstName and lastName when given, otherwise fullName split at its first space (what the web form sends). */
    private static String[] namesOf(RegisterRequest request) {
        boolean given = (request.firstName() != null && !request.firstName().isBlank())
                || (request.lastName() != null && !request.lastName().isBlank());
        if (given || request.fullName() == null || request.fullName().isBlank()) {
            return new String[] {request.firstName(), request.lastName()};
        }
        String trimmed = request.fullName().trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? new String[] {trimmed, "-"}
                : new String[] {trimmed.substring(0, space), trimmed.substring(space + 1).trim()};
    }

    private static PatientSummary toSummary(Patient patient) {
        Branch branch = patient.getBranch();
        return new PatientSummary(patient.getId(), patient.getPatientHn(),
                fullName(patient), patient.getPhone(), true,
                branch == null ? null : branch.getId(), branch == null ? null : branch.getBranchCode(),
                branch == null ? null : branch.getBranchName());
    }

    private static PatientProfile toProfile(Patient patient) {
        Branch branch = patient.getBranch();
        String identityDocumentType = patient.getNationalId() != null ? "NATIONAL_ID"
                : patient.getPassportNo() != null ? "PASSPORT" : null;
        String identityNumber = patient.getNationalId() != null ? patient.getNationalId() : patient.getPassportNo();
        return new PatientProfile(
                patient.getId(), patient.getPatientHn(), fullName(patient),
                patient.getPhone(), true,
                branch == null ? null : new BranchSummary(branch.getId(), branch.getBranchCode(), branch.getBranchName(), branch.isBranchStatus()),
                patient.getTitle(), patient.getBirthdate(), patient.getGender() == null ? null : patient.getGender().name(),
                identityDocumentType, identityNumber, patient.getAddress(),
                patient.isDrugAllergy(), patient.isFoodAllergy(), patient.getChronicDisease(),
                patient.getEmergencyContactName(), patient.getEmergencyContactRelationship(),
                patient.getEmergencyContactPhone(), patient.isPrivacyConsent(),
                patient.getPatientNickname(), patient.getBloodGroup() == null ? null : patient.getBloodGroup().name(),
                patient.getNationality(),
                patient.getCustomerGroup() == null ? null : patient.getCustomerGroup().getValueName(),
                patient.getReferralChannel() == null ? null : patient.getReferralChannel().getValueName(),
                patient.getInsuranceCompany() == null ? null : patient.getInsuranceCompany().getValueName(),
                patient.getCustomerType() == null ? null : patient.getCustomerType().name(),
                patient.getPatientFname(), patient.getPatientLname(), patient.getPatientMiddleName(), patient.getEmail());
    }

    private static String validEmail(String value) {
        if (value == null || value.isBlank()) return null;
        String email = value.trim();
        if (email.length() > 254 || !email.matches("^[^@.\\s]+(\\.[^@.\\s]+)*@[^@.\\s]+(\\.[^@.\\s]+)+$")) {
            throw new IllegalArgumentException("รูปแบบอีเมลผู้ป่วยไม่ถูกต้อง");
        }
        return email;
    }

    private static String middleNameOf(RegisterRequest request) {
        var value = request.middleName();
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException("ชื่อกลางต้องเป็นข้อความหรือ null");
        return value.textValue();
    }

    private static String fullName(Patient patient) {
        return java.util.stream.Stream.of(patient.getPatientFname(), patient.getPatientMiddleName(), patient.getPatientLname())
                .filter(value -> value != null && !value.isBlank()).collect(java.util.stream.Collectors.joining(" "));
    }

    private static CustomerType parseCustomerType(String value) {
        if (value == null) return CustomerType.Thai;
        try {
            return CustomerType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return "ต่างชาติ".equals(value) || "Foreigner".equalsIgnoreCase(value) ? CustomerType.Foreigner : CustomerType.Thai;
        }
    }

    private MasterDataValue resolveMasterDataValue(String category, String name) {
        if (name == null || name.isBlank()) return null;
        return masterDataValues.findAll().stream()
                .filter(value -> category.equalsIgnoreCase(value.getType().getTypeName())
                        && name.trim().equalsIgnoreCase(value.getValueName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบค่า " + name + " ใน " + category));
    }

    private static Gender parseGender(String value) {
        if (value == null) return null;
        try {
            return Gender.valueOf(value);
        } catch (IllegalArgumentException e) {
            return switch (value) {
                case "ชาย" -> Gender.Male;
                case "หญิง" -> Gender.Female;
                case "ไม่ระบุ" -> Gender.NotSpecified;
                default -> Gender.Other;
            };
        }
    }

    private static BloodGroup parseBloodGroup(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return BloodGroup.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " ห้ามว่าง");
        return value.trim();
    }
}
