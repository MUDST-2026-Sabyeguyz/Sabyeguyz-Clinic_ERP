package com.sabyeguyz.clinicv2.patient;

import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.enums.BloodGroup;
import com.sabyeguyz.clinicv2.enums.CustomerType;
import com.sabyeguyz.clinicv2.enums.Gender;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's patientApi (GET/POST/PUT
 * /api/patients). Known gaps vs. the old PatientProfile type, all flagged
 * for the team since the v2 Patient data dictionary simply does not define
 * these columns:
 * <ul>
 *   <li>drugAllergy, foodAllergy, chronicDisease, emergencyContactName/
 *   Relationship/Phone, privacyConsent — no equivalent column exists;
 *   always returned as false/null here.</li>
 *   <li>identityDocumentType/identityNumber are derived from v2's separate
 *   nationalId/passportNo columns (NATIONAL_ID wins if both are set).</li>
 *   <li>customerType (old: a free-text Master Data value like "VIP") maps
 *   to v2's customerGroup, NOT v2's own customerType enum (Thai/Foreigner)
 *   — those are different concepts that happen to share an old field name.
 *   v2's real Thai/Foreigner value is exposed as an extra
 *   {@code customerTypeCode} field the old frontend type doesn't know
 *   about yet.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {
    private final PatientService service;
    private final PatientRepository repository;
    private final BranchRepository branches;
    private final CurrentActor actor;

    public PatientController(PatientService service, PatientRepository repository, BranchRepository branches,
                              CurrentActor actor) {
        this.service = service;
        this.repository = repository;
        this.branches = branches;
        this.actor = actor;
    }

    public record RegisterRequest(
            String branchCode, String customerType, String title, String firstName, String lastName,
            String gender, String phone, String genderDetail, String nickname, String identityDocumentType,
            String identityNumber, LocalDate birthDate, String bloodGroup, String nationality, String address) {}

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
            String customerType, String customerSource, String insuranceCompany, String customerTypeCode) {}

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
        Sort sortOrder = "nameDesc".equals(sort)
                ? Sort.by("patientFname").descending().and(Sort.by("patientLname").descending())
                : Sort.by("patientFname").ascending().and(Sort.by("patientLname").ascending());

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
        PatientRegistration registration = new PatientRegistration(
                branch,
                parseCustomerType(request.customerType()),
                request.title(),
                request.firstName(),
                request.lastName(),
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
                null, null, null, null);
        Patient patient = service.register(registration, actor.currentId());
        return toSummary(patient);
    }

    @PutMapping("/{id}")
    public PatientProfile update(@PathVariable Long id, @RequestBody RegisterRequest request) {
        Patient patient = service.updateProfile(id, request.nickname(), request.phone(), request.address(),
                parseBloodGroup(request.bloodGroup()), request.nationality(), actor.currentId());
        return toProfile(patient);
    }

    private static PatientSummary toSummary(Patient patient) {
        Branch branch = patient.getBranch();
        return new PatientSummary(patient.getId(), patient.getPatientHn(),
                patient.getPatientFname() + " " + patient.getPatientLname(), patient.getPhone(), true,
                branch == null ? null : branch.getId(), branch == null ? null : branch.getBranchCode(),
                branch == null ? null : branch.getBranchName());
    }

    private static PatientProfile toProfile(Patient patient) {
        Branch branch = patient.getBranch();
        String identityDocumentType = patient.getNationalId() != null ? "NATIONAL_ID"
                : patient.getPassportNo() != null ? "PASSPORT" : null;
        String identityNumber = patient.getNationalId() != null ? patient.getNationalId() : patient.getPassportNo();
        return new PatientProfile(
                patient.getId(), patient.getPatientHn(), patient.getPatientFname() + " " + patient.getPatientLname(),
                patient.getPhone(), true,
                branch == null ? null : new BranchSummary(branch.getId(), branch.getBranchCode(), branch.getBranchName(), branch.isBranchStatus()),
                patient.getTitle(), patient.getBirthdate(), patient.getGender() == null ? null : patient.getGender().name(),
                identityDocumentType, identityNumber, patient.getAddress(),
                false, false, null, null, null, null, false,
                patient.getPatientNickname(), patient.getBloodGroup() == null ? null : patient.getBloodGroup().name(),
                patient.getNationality(),
                patient.getCustomerGroup() == null ? null : patient.getCustomerGroup().getValueName(),
                patient.getReferralChannel() == null ? null : patient.getReferralChannel().getValueName(),
                patient.getInsuranceCompany() == null ? null : patient.getInsuranceCompany().getValueName(),
                patient.getCustomerType() == null ? null : patient.getCustomerType().name());
    }

    private static CustomerType parseCustomerType(String value) {
        if (value == null) return CustomerType.Thai;
        try {
            return CustomerType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return "ต่างชาติ".equals(value) || "Foreigner".equalsIgnoreCase(value) ? CustomerType.Foreigner : CustomerType.Thai;
        }
    }

    private static Gender parseGender(String value) {
        if (value == null) return null;
        try {
            return Gender.valueOf(value);
        } catch (IllegalArgumentException e) {
            return switch (value) {
                case "ชาย" -> Gender.Male;
                case "หญิง" -> Gender.Female;
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
