package com.sabyeguyz.clinic.databasev2.patient;

import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2BranchRepository;
import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2ConflictException;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import com.sabyeguyz.clinic.databasev2.masterdata.DatabaseV2MasterDataRepository;
import com.sabyeguyz.clinic.databasev2.staff.DatabaseV2StaffController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("database-v2")
@RequestMapping("/api/patients")
public class DatabaseV2PatientController {

    private final DatabaseV2PatientRepository patientRepository;
    private final DatabaseV2BranchRepository branchRepository;
    private final DatabaseV2MasterDataRepository masterDataRepository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2PatientController(
            DatabaseV2PatientRepository patientRepository,
            DatabaseV2BranchRepository branchRepository,
            DatabaseV2MasterDataRepository masterDataRepository,
            CurrentActorProvider actorProvider) {
        this.patientRepository = patientRepository;
        this.branchRepository = branchRepository;
        this.masterDataRepository = masterDataRepository;
        this.actorProvider = actorProvider;
    }

    public record RegisterRequest(
            @NotBlank String branchCode,
            String username,
            String email,
            String password,
            String title,
            String fullName,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String gender,
            String phone,
            String identityDocumentType,
            String identityNumber,
            String address,
            Boolean drugAllergy,
            Boolean foodAllergy,
            String chronicDisease,
            String emergencyContactName,
            String emergencyContactRelationship,
            String emergencyContactPhone,
            Boolean privacyConsent,
            String nickname,
            String bloodGroup,
            String nationality,
            String customerType,
            String customerSource,
            String insuranceCompany
    ) {}

    public record PatientResponse(
            Long id,
            String hn,
            String fullName,
            String phone,
            boolean active,
            Long branchId,
            String branchCode,
            String branchName
    ) {}

    public record BranchSummary(Long id, String code, String name, boolean active) {}

    public record PatientProfileResponse(
            Long id,
            String hn,
            BranchSummary branch,
            String title,
            String fullName,
            LocalDate birthDate,
            String gender,
            String phone,
            String identityDocumentType,
            String identityNumber,
            String address,
            boolean drugAllergy,
            boolean foodAllergy,
            String chronicDisease,
            String emergencyContactName,
            String emergencyContactRelationship,
            String emergencyContactPhone,
            boolean privacyConsent,
            boolean active,
            String nickname,
            String email,
            String bloodGroup,
            String nationality,
            String customerType,
            String customerSource,
            String insuranceCompany
    ) {}

    public record PageResponse(
            List<PatientResponse> items,
            int page,
            int size,
            long totalItems,
            int totalPages
    ) {}

    @GetMapping
    public PageResponse search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String branchCode,
            @RequestParam(defaultValue = "nameAsc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int limit = Math.max(1, Math.min(size, 100));
        int offset = Math.max(0, page) * limit;

        List<DatabaseV2PatientRepository.PatientRecord> records = patientRepository.search(query, branchCode, limit, offset);
        int total = patientRepository.countSearch(query, branchCode);
        int totalPages = (int) Math.ceil((double) total / limit);

        List<PatientResponse> items = records.stream().map(DatabaseV2PatientController::toSimpleResponse).toList();
        return new PageResponse(items, page, limit, total, totalPages);
    }

    @GetMapping("/search")
    public PageResponse searchAlias(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String branchCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return search(query, branchCode, "nameAsc", page, size);
    }

    @GetMapping("/{id}")
    public PatientProfileResponse getProfile(@PathVariable Long id) {
        DatabaseV2PatientRepository.PatientRecord r = patientRepository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบผู้ป่วย"));
        return toProfileResponse(r);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse register(@Valid @RequestBody RegisterRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);

        DatabaseV2Branch branch = branchRepository.findByCode(request.branchCode().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + request.branchCode()));

        // Resolve names
        String fname;
        String lname;
        if (request.firstName() != null && !request.firstName().isBlank()) {
            fname = request.firstName().trim();
            lname = request.lastName() != null && !request.lastName().isBlank() ? request.lastName().trim() : "-";
        } else if (request.fullName() != null && !request.fullName().isBlank()) {
            String[] split = DatabaseV2StaffController.splitFullName(request.fullName());
            fname = split[0];
            lname = split[1];
        } else {
            throw new IllegalArgumentException("กรุณากรอกชื่อผู้ป่วย");
        }

        // Customer Type
        boolean isForeigner = "Foreigner".equalsIgnoreCase(request.customerType())
                || "ต่างชาติ".equals(request.nationality())
                || "PASSPORT".equalsIgnoreCase(request.identityDocumentType());
        String customerType = isForeigner ? "Foreigner" : "Thai";

        // Identity verification
        String nationalId = null;
        String passportNo = null;
        String idNumber = request.identityNumber() != null ? request.identityNumber().trim() : "";
        if (!isForeigner) {
            String digits = idNumber.replaceAll("\\D", "");
            if (digits.length() != 13) {
                throw new IllegalArgumentException("เลขประจำตัวประชาชนต้องเป็นตัวเลข 13 หลัก");
            }
            if (patientRepository.findByNationalId(digits).isPresent()) {
                throw new DatabaseV2ConflictException("เลขประจำตัวประชาชนนี้มีอยู่ในระบบแล้ว");
            }
            nationalId = digits;
        } else {
            if (idNumber.isBlank()) {
                throw new IllegalArgumentException("กรุณากรอกหมายเลขหนังสือเดินทางสำหรับชาวต่างชาติ");
            }
            if (patientRepository.findByPassportNo(idNumber).isPresent()) {
                throw new DatabaseV2ConflictException("หมายเลขหนังสือเดินทางนี้มีอยู่ในระบบแล้ว");
            }
            passportNo = idNumber;
        }

        // Title
        String title = normalizeTitle(request.title(), request.gender());

        // Gender
        String gender = normalizeGender(request.gender());

        // Blood Group
        String bloodGroup = normalizeBloodGroup(request.bloodGroup());

        // Master Data lookups
        Long customerGroupValId = resolveMasterDataVal("Customer Group", request.customerType(), actorId);
        Long referralChannelValId = resolveMasterDataVal("Referral Channel", request.customerSource(), actorId);
        Long insuranceCompanyValId = resolveMasterDataVal("Insurance Company", request.insuranceCompany(), actorId);

        // Generate HN atomically
        LocalDate today = LocalDate.now();
        String hn = patientRepository.generateHn(branch.getId(), branch.getCode(), today);

        DatabaseV2PatientRepository.PatientRecord created = patientRepository.create(
                hn, customerType, title, fname, lname,
                gender, null, request.nickname(),
                request.phone() != null ? request.phone().trim() : "",
                branch.getId(), nationalId, passportNo,
                request.birthDate() != null ? request.birthDate() : LocalDate.of(1990, 1, 1),
                bloodGroup, request.nationality() != null ? request.nationality().trim() : (isForeigner ? "Foreigner" : "ไทย"),
                request.address() != null ? request.address().trim() : "",
                actorId, customerGroupValId, referralChannelValId, insuranceCompanyValId
        );

        return toSimpleResponse(created);
    }

    @PutMapping("/{id}")
    public PatientProfileResponse update(@PathVariable Long id, @Valid @RequestBody RegisterRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2PatientRepository.PatientRecord existing = patientRepository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบผู้ป่วย"));

        // Resolve names
        String fname;
        String lname;
        if (request.firstName() != null && !request.firstName().isBlank()) {
            fname = request.firstName().trim();
            lname = request.lastName() != null && !request.lastName().isBlank() ? request.lastName().trim() : "-";
        } else if (request.fullName() != null && !request.fullName().isBlank()) {
            String[] split = DatabaseV2StaffController.splitFullName(request.fullName());
            fname = split[0];
            lname = split[1];
        } else {
            fname = existing.firstName();
            lname = existing.lastName();
        }

        String idNumber = request.identityNumber() != null ? request.identityNumber().trim() : "";
        String nationalId = existing.nationalId();
        String passportNo = existing.passportNo();

        if ("Thai".equals(existing.customerType()) && !idNumber.isBlank()) {
            String digits = idNumber.replaceAll("\\D", "");
            if (digits.length() == 13) {
                patientRepository.findByNationalId(digits)
                        .filter(p -> !p.id().equals(id))
                        .ifPresent(p -> { throw new DatabaseV2ConflictException("เลขประจำตัวประชาชนนี้มีอยู่ในระบบแล้ว"); });
                nationalId = digits;
            }
        } else if ("Foreigner".equals(existing.customerType()) && !idNumber.isBlank()) {
            patientRepository.findByPassportNo(idNumber)
                    .filter(p -> !p.id().equals(id))
                    .ifPresent(p -> { throw new DatabaseV2ConflictException("หมายเลขหนังสือเดินทางนี้มีอยู่ในระบบแล้ว"); });
            passportNo = idNumber;
        }

        String title = normalizeTitle(request.title(), request.gender());
        String gender = normalizeGender(request.gender());
        String bloodGroup = normalizeBloodGroup(request.bloodGroup());

        Long customerGroupValId = resolveMasterDataVal("Customer Group", request.customerType(), actorId);
        Long referralChannelValId = resolveMasterDataVal("Referral Channel", request.customerSource(), actorId);
        Long insuranceCompanyValId = resolveMasterDataVal("Insurance Company", request.insuranceCompany(), actorId);

        DatabaseV2PatientRepository.PatientRecord updated = patientRepository.update(
                id, title, fname, lname,
                gender, existing.genderDetail(), request.nickname(),
                request.phone() != null ? request.phone().trim() : existing.phone(),
                nationalId, passportNo,
                request.birthDate() != null ? request.birthDate() : existing.birthdate(),
                bloodGroup, request.nationality() != null ? request.nationality().trim() : existing.nationality(),
                request.address() != null ? request.address().trim() : existing.address(),
                actorId,
                customerGroupValId != null ? customerGroupValId : existing.customerGroupValueId(),
                referralChannelValId != null ? referralChannelValId : existing.referralChannelValueId(),
                insuranceCompanyValId != null ? insuranceCompanyValId : existing.insuranceCompanyValueId()
        );

        return toProfileResponse(updated);
    }

    private Long resolveMasterDataVal(String category, String name, Long actorId) {
        if (name == null || name.isBlank()) return null;
        try {
            return masterDataRepository.findByCategory(category).stream()
                    .filter(v -> v.name().equalsIgnoreCase(name.trim()))
                    .map(DatabaseV2MasterDataRepository.MasterDataRecord::id)
                    .findFirst()
                    .orElseGet(() -> {
                        var created = masterDataRepository.create(category, name.trim(), 0, true, actorId);
                        return created != null ? created.id() : null;
                    });
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeTitle(String title, String gender) {
        if (title != null) {
            String t = title.trim();
            if ("นาย".equals(t) || "นาง".equals(t) || "นางสาว".equals(t) || "ด.ช.".equals(t) || "ด.ญ.".equals(t)) {
                return t;
            }
        }
        return "หญิง".equalsIgnoreCase(gender) || "Female".equalsIgnoreCase(gender) ? "นางสาว" : "นาย";
    }

    private static String normalizeGender(String gender) {
        if (gender != null) {
            String g = gender.trim();
            if ("ชาย".equalsIgnoreCase(g) || "Male".equalsIgnoreCase(g) || "M".equalsIgnoreCase(g)) return "Male";
            if ("หญิง".equalsIgnoreCase(g) || "Female".equalsIgnoreCase(g) || "F".equalsIgnoreCase(g)) return "Female";
        }
        return "Other";
    }

    private static String normalizeBloodGroup(String bg) {
        if (bg != null) {
            String b = bg.trim().toUpperCase();
            if ("A".equals(b) || "B".equals(b) || "O".equals(b) || "AB".equals(b)) return b;
        }
        return null;
    }

    private static PatientResponse toSimpleResponse(DatabaseV2PatientRepository.PatientRecord r) {
        return new PatientResponse(
                r.id(),
                r.hn(),
                r.fullName(),
                r.phone(),
                true,
                r.branchId(),
                r.branchCode(),
                r.branchName()
        );
    }

    private static PatientProfileResponse toProfileResponse(DatabaseV2PatientRepository.PatientRecord r) {
        return new PatientProfileResponse(
                r.id(),
                r.hn(),
                new BranchSummary(r.branchId(), r.branchCode(), r.branchName(), true),
                r.title(),
                r.fullName(),
                r.birthdate(),
                r.gender(),
                r.phone(),
                r.nationalId() != null ? "NATIONAL_ID" : "PASSPORT",
                r.nationalId() != null ? r.nationalId() : (r.passportNo() != null ? r.passportNo() : ""),
                r.address(),
                false,
                false,
                "",
                r.fullName(),
                "ญาติ",
                r.phone(),
                true,
                true,
                r.nickname(),
                null,
                r.bloodGroup(),
                r.nationality(),
                r.customerType(),
                r.referralChannelName(),
                r.insuranceCompanyName()
        );
    }
}
