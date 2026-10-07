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
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@Profile("database-v2")
@RequestMapping("/api/patients")
public class DatabaseV2PatientController {

    private final DatabaseV2PatientRepository patientRepository;
    private final DatabaseV2BranchRepository branchRepository;
    private final DatabaseV2MasterDataRepository masterDataRepository;
    private final CurrentActorProvider actorProvider;
    private final Clock patientBusinessClock;
    private final com.sabyeguyz.clinic.databasev2.security.BranchAccessGuard branchAccess;

    public DatabaseV2PatientController(
            DatabaseV2PatientRepository patientRepository,
            DatabaseV2BranchRepository branchRepository,
            DatabaseV2MasterDataRepository masterDataRepository,
            CurrentActorProvider actorProvider,
            Clock patientBusinessClock,
            com.sabyeguyz.clinic.databasev2.security.BranchAccessGuard branchAccess) {
        this.patientRepository = patientRepository;
        this.branchRepository = branchRepository;
        this.masterDataRepository = masterDataRepository;
        this.actorProvider = actorProvider;
        this.patientBusinessClock = patientBusinessClock;
        this.branchAccess = branchAccess;
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
            String insuranceCompany,
            String patientType
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
            boolean active,
            String nickname,
            String email,
            String bloodGroup,
            String nationality,
            String patientType,
            String customerGroup,
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
    public PageResponse searchHttp(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String branchCode,
            @RequestParam(defaultValue = "nameAsc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Authentication auth) {
        validatePage(page, size);
        Long actor = branchAccess.actorId(auth);
        var records = patientRepository.search(query, branchCode, size, Math.multiplyExact(page, size), actor);
        int total = patientRepository.countSearch(query, branchCode, actor);
        return new PageResponse(records.stream().map(DatabaseV2PatientController::toSimpleResponse).toList(),
                page, size, total, (int) Math.ceil((double) total / size));
    }

    public PageResponse search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String branchCode,
            @RequestParam(defaultValue = "nameAsc") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validatePage(page, size);
        int limit = size;
        int offset;
        try {
            offset = Math.multiplyExact(page, limit);
        } catch (ArithmeticException tooLarge) {
            throw new IllegalArgumentException("page เกินขอบเขตที่รองรับ", tooLarge);
        }

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
            @RequestParam(defaultValue = "20") int size, Authentication auth
    ) {
        return searchHttp(query, branchCode, "nameAsc", page, size, auth);
    }

    @GetMapping("/{id}")
    public PatientProfileResponse getProfileHttp(@PathVariable Long id, Authentication auth) {
        var record = patientRepository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบผู้ป่วย"));
        branchAccess.require(auth, record.branchId());
        return toProfileResponse(record);
    }

    public PatientProfileResponse getProfile(Long id) {
        DatabaseV2PatientRepository.PatientRecord r = patientRepository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบผู้ป่วย"));
        return toProfileResponse(r);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public PatientResponse register(@Valid @RequestBody RegisterRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);

        DatabaseV2Branch branch = branchRepository.findByCode(request.branchCode().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + request.branchCode()));

        branchAccess.require(auth, branch.getId());
        if (!branch.active()) throw new IllegalArgumentException("สาขานี้ปิดใช้งาน");
        String[] names = resolveNames(request);
        String fname = names[0];
        String lname = names[1];
        String customerType = resolvePatientType(request);
        boolean isForeigner = "Foreigner".equals(customerType);
        requirePhone(request.phone(), customerType);

        // Identity verification
        String nationalId = null;
        String passportNo = null;
        String idNumber = request.identityNumber() != null ? request.identityNumber().trim() : "";
        if (usesNationalId(request.identityDocumentType(), customerType) && !idNumber.isBlank()) {
            String digits = idNumber.replaceAll("\\D", "");
            if (digits.length() != 13) {
                throw new IllegalArgumentException("เลขประจำตัวประชาชนต้องเป็นตัวเลข 13 หลัก");
            }
            if (patientRepository.findByNationalId(digits).isPresent()) {
                throw new DatabaseV2ConflictException("เลขประจำตัวประชาชนนี้มีอยู่ในระบบแล้ว");
            }
            nationalId = digits;
        } else if (!idNumber.isBlank()) {
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
        Long customerGroupValId = resolveMasterDataVal("Customer Group", request.customerType(), null);
        Long referralChannelValId = resolveMasterDataVal("Referral Channel", request.customerSource(), null);
        Long insuranceCompanyValId = resolveMasterDataVal("Insurance Company", request.insuranceCompany(), null);

        // Generate HN atomically
        LocalDate today = LocalDate.now(patientBusinessClock);
        String hn = patientRepository.generateHn(branch.getId(), branch.getCode(), today);

        DatabaseV2PatientRepository.PatientRecord created = patientRepository.create(
                hn, customerType, title, fname, lname,
                gender, null, normalizeOptional(request.nickname()),
                request.phone() != null ? request.phone().trim() : "",
                branch.getId(), nationalId, passportNo,
                request.birthDate(),
                bloodGroup, request.nationality() != null ? request.nationality().trim() : (isForeigner ? "Foreigner" : "ไทย"),
                normalizeOptional(request.address()),
                actorId, customerGroupValId, referralChannelValId, insuranceCompanyValId
        );

        return toSimpleResponse(created);
    }

    @PutMapping("/{id}")
    @Transactional
    public PatientProfileResponse updateHttp(@PathVariable Long id,
            @Valid @RequestBody PatientProfileUpdateRequest request, Authentication auth) {
        return updatePatient(id, request.values(), auth, request.isBirthDateProvided());
    }

    // Retained direct-call contract for the existing controller/JDBC regressions.
    @Transactional
    public PatientProfileResponse update(Long id, RegisterRequest request, Authentication auth) {
        return updatePatient(id, request, auth, true);
    }

    private PatientProfileResponse updatePatient(Long id, RegisterRequest request,
            Authentication auth, boolean birthDateProvided) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2PatientRepository.PatientRecord existing = patientRepository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบผู้ป่วย"));
        branchAccess.require(auth, existing.branchId());
        String[] names = resolveNames(request);
        String fname = names[0];
        String lname = names[1];
        requirePhone(request.phone(), existing.customerType());

        String idNumber = request.identityNumber() != null ? request.identityNumber().trim() : "";
        String nationalId = existing.nationalId();
        String passportNo = existing.passportNo();

        if (usesNationalId(request.identityDocumentType(), existing.customerType()) && !idNumber.isBlank()) {
            String digits = idNumber.replaceAll("\\D", "");
            if (digits.length() != 13) {
                throw new IllegalArgumentException("เลขประจำตัวประชาชนต้องเป็นตัวเลข 13 หลัก");
            }
            patientRepository.findByNationalId(digits)
                        .filter(p -> !p.id().equals(id))
                        .ifPresent(p -> { throw new DatabaseV2ConflictException("เลขประจำตัวประชาชนนี้มีอยู่ในระบบแล้ว"); });
            nationalId = digits;
        } else if (!idNumber.isBlank()) {
            patientRepository.findByPassportNo(idNumber)
                    .filter(p -> !p.id().equals(id))
                    .ifPresent(p -> { throw new DatabaseV2ConflictException("หมายเลขหนังสือเดินทางนี้มีอยู่ในระบบแล้ว"); });
            passportNo = idNumber;
        }

        String title = normalizeTitle(request.title(), request.gender());
        String gender = normalizeGender(request.gender());
        String bloodGroup = normalizeBloodGroup(request.bloodGroup());

        Long customerGroupValId = resolveMasterDataVal("Customer Group", request.customerType(), existing.customerGroupValueId());
        Long referralChannelValId = resolveMasterDataVal("Referral Channel", request.customerSource(), existing.referralChannelValueId());
        Long insuranceCompanyValId = resolveMasterDataVal("Insurance Company", request.insuranceCompany(), existing.insuranceCompanyValueId());

        DatabaseV2PatientRepository.PatientRecord updated = patientRepository.update(
                id, title, fname, lname,
                gender, existing.genderDetail(), normalizeOptional(request.nickname()),
                request.phone() != null ? request.phone().trim() : existing.phone(),
                nationalId, passportNo,
                birthDateProvided ? request.birthDate() : existing.birthdate(),
                bloodGroup, request.nationality() != null ? request.nationality().trim() : existing.nationality(),
                normalizeOptional(request.address()),
                actorId,
                customerGroupValId,
                referralChannelValId,
                insuranceCompanyValId
        );

        return toProfileResponse(updated);
    }

    private Long resolveMasterDataVal(String category, String name, Long existingId) {
        if (name == null) return existingId;
        if (name.isBlank()) return null;
        // Preserve an existing historical relationship when its setting is retired.
        if (existingId != null && masterDataRepository.findById(existingId)
                .filter(value -> value.name().equalsIgnoreCase(name.trim())).isPresent()) return existingId;
        return masterDataRepository.findByCategory(category).stream()
                .filter(DatabaseV2MasterDataRepository.MasterDataRecord::active)
                .filter(value -> value.name().equalsIgnoreCase(name.trim()))
                .map(DatabaseV2MasterDataRepository.MasterDataRecord::id)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("ไม่พบข้อมูลพื้นฐานที่เปิดใช้งาน: " + category));
    }

    private static void validatePage(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page ต้องไม่น้อยกว่า 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size ต้องอยู่ระหว่าง 1 ถึง 100");
        try { Math.multiplyExact(page, size); }
        catch (ArithmeticException tooLarge) { throw new IllegalArgumentException("page เกินขอบเขตที่รองรับ", tooLarge); }
    }

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String[] resolveNames(RegisterRequest request) {
        String first = normalizeOptional(request.firstName());
        String last = normalizeOptional(request.lastName());
        if (first == null && last == null && normalizeOptional(request.fullName()) != null) {
            String[] legacy = DatabaseV2StaffController.splitFullName(request.fullName().trim());
            first = legacy[0];
            last = "-".equals(legacy[1]) ? null : legacy[1];
        }
        if (first == null || last == null) {
            throw new IllegalArgumentException("กรุณากรอกชื่อและนามสกุลผู้ป่วย");
        }
        return new String[] {first, last};
    }

    private static String resolvePatientType(RegisterRequest request) {
        String explicit = normalizeOptional(request.patientType());
        if (explicit != null) {
            if ("Thai".equalsIgnoreCase(explicit)) return "Thai";
            if ("Foreigner".equalsIgnoreCase(explicit)) return "Foreigner";
            throw new IllegalArgumentException("ประเภทผู้ป่วยต้องเป็น Thai หรือ Foreigner");
        }
        return "ต่างชาติ".equals(request.nationality())
                || "PASSPORT".equalsIgnoreCase(request.identityDocumentType()) ? "Foreigner" : "Thai";
    }

    private static boolean usesNationalId(String documentType, String patientType) {
        String type = normalizeOptional(documentType);
        if (type == null) return "Thai".equals(patientType);
        if ("NATIONAL_ID".equalsIgnoreCase(type)) return true;
        if ("PASSPORT".equalsIgnoreCase(type)) return false;
        throw new IllegalArgumentException("ประเภทเอกสารประจำตัวไม่ถูกต้อง");
    }

    private static void requirePhone(String phone, String patientType) {
        // Foreign requiredness remains pending; retain legacy compatibility for that path.
        if ("Thai".equals(patientType) && normalizeOptional(phone) == null) {
            throw new IllegalArgumentException("กรุณากรอกเบอร์โทรศัพท์");
        }
    }

    private static String normalizeTitle(String title, String gender) {
        String value = normalizeOptional(title);
        if (value == null) throw new IllegalArgumentException("กรุณากรอกคำนำหน้า");
        if (!List.of("นาย", "นาง", "นางสาว", "ด.ช.", "ด.ญ.").contains(value)) {
            throw new IllegalArgumentException("คำนำหน้าไม่ถูกต้อง");
        }
        return value;
    }

    private static String normalizeGender(String gender) {
        String value = normalizeOptional(gender);
        if (value == null) throw new IllegalArgumentException("กรุณาระบุเพศ");
        if ("ชาย".equalsIgnoreCase(value) || "Male".equalsIgnoreCase(value) || "M".equalsIgnoreCase(value)) return "Male";
        if ("หญิง".equalsIgnoreCase(value) || "Female".equalsIgnoreCase(value) || "F".equalsIgnoreCase(value)) return "Female";
        if ("Other".equalsIgnoreCase(value) || "อื่นๆ".equals(value) || "อื่น ๆ".equals(value)) return "Other";
        throw new IllegalArgumentException("เพศไม่ถูกต้อง");
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
                r.firstName(),
                r.lastName(),
                r.birthdate(),
                r.gender(),
                r.phone(),
                r.nationalId() != null ? "NATIONAL_ID" : (r.passportNo() != null ? "PASSPORT" : ""),
                r.nationalId() != null ? r.nationalId() : (r.passportNo() != null ? r.passportNo() : ""),
                r.address(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                r.nickname(),
                null,
                r.bloodGroup(),
                r.nationality(),
                r.customerType(),
                r.customerGroupName(),
                r.referralChannelName(),
                r.insuranceCompanyName()
        );
    }
}
