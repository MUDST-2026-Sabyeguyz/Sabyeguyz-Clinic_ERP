package com.sabyeguyz.clinicv2.commission;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.course.CourseSaleStaffShare;
import com.sabyeguyz.clinicv2.course.CourseSaleStaffShareRepository;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.role.PermissionRepository;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's commissionApi. {@code report} is
 * approximated by grouping the credited sales of Courses (Course_sale_staff_share,
 * so a shared sale counts for each staff member's own part) by
 * staff within the date range and re-resolving the Tier for that base -
 * the old API grouped Sale totals directly; this is closer to how v2's
 * Tier actually gets picked (see CourseService/MonthlyCommissionClosingService).
 */
@Transactional
@PreAuthorize("hasRole('MANAGER')")
@RestController
@RequestMapping("/api/commission")
public class CommissionController {
    private final CommissionSchemeService schemeService;
    private final CommissionTierResolver tierResolver;
    private final CommissionSchemeRepository schemes;
    private final MonthlyCommissionClosingService closingService;
    private final MonthlyCommissionClosingRepository closings;
    private final CommissionAllocationLedgerRepository allocationLedger;
    private final TreatmentFeeRuleRepository treatmentFeeRules;
    private final CourseRepository courses;
    private final CourseSaleStaffShareRepository saleShares;
    private final StaffRepository staff;
    private final PermissionRepository permissions;
    private final CurrentActor actor;
    private final CommissionRateOverrideService overrideService;
    private final com.sabyeguyz.clinicv2.audit.AuditLogService audit;

    public CommissionController(CommissionSchemeService schemeService, CommissionTierResolver tierResolver,
                                 CommissionSchemeRepository schemes, MonthlyCommissionClosingService closingService,
                                 MonthlyCommissionClosingRepository closings,
                                 CommissionAllocationLedgerRepository allocationLedger,
                                 TreatmentFeeRuleRepository treatmentFeeRules, CourseRepository courses,
                                 CourseSaleStaffShareRepository saleShares,
                                 StaffRepository staff, PermissionRepository permissions, CurrentActor actor,
                                 CommissionRateOverrideService overrideService,
                                 com.sabyeguyz.clinicv2.audit.AuditLogService audit) {
        this.schemeService = schemeService;
        this.tierResolver = tierResolver;
        this.schemes = schemes;
        this.closingService = closingService;
        this.closings = closings;
        this.allocationLedger = allocationLedger;
        this.treatmentFeeRules = treatmentFeeRules;
        this.courses = courses;
        this.saleShares = saleShares;
        this.staff = staff;
        this.permissions = permissions;
        this.actor = actor;
        this.overrideService = overrideService;
        this.audit = audit;
    }

    public record TierInput(BigDecimal minimum, BigDecimal maximum, BigDecimal rate, int order) {}
    public record SchemeRequest(String name, String version, LocalDate effectiveFrom, LocalDate effectiveTo,
                                 List<TierInput> tiers) {}

    public record SchemeResponse(Long id, String name, int version, LocalDate effectiveFrom, LocalDate effectiveTo) {}
    public record TierResponse(Long id, int order, BigDecimal minimum, BigDecimal maximum, BigDecimal rate, boolean active) {}
    public record ReportRow(Long staffId, String staffName, String branchCode, String branchName, int saleCount,
                             BigDecimal base, BigDecimal rate, BigDecimal commission) {}
    public record TreatmentReportRow(Long id, LocalDate visitDate, Long coursePurchaseId, String courseCode,
                                      Long patientId, String patientHn, String patientName, String branchCode,
                                      String branchName, Long treatingStaffId, String treatingStaffName,
                                      Long ownerStaffId, String ownerStaffName, BigDecimal gross,
                                      BigDecimal treatmentFee, BigDecimal ownerNet) {}
    public record ClosingResponse(Long id, String month, Long employeeId, String employeeName, BigDecimal sales,
                                   String version, BigDecimal rate, String status) {}
    public record TreatmentFeeResponse(Long id, Long staffId, String feeType, BigDecimal feeValue, String percentageBase) {}

    @GetMapping("/schemes")
    public List<SchemeResponse> schemes() {
        return schemeService.list().stream().map(CommissionController::toSchemeResponse).toList();
    }

    @GetMapping("/schemes/{id}/tiers")
    public List<TierResponse> tiers(@PathVariable Long id) {
        return schemeService.tiersOf(id).stream().map(CommissionController::toTierResponse).toList();
    }

    @PostMapping("/schemes")
    @ResponseStatus(HttpStatus.CREATED)
    public SchemeResponse createScheme(@RequestBody SchemeRequest request) {
        Permission permission = resolveOrCreatePermission("COMMISSION_SCHEME_MANAGE");
        List<CommissionSchemeService.TierInput> inputs = request.tiers().stream()
                .map(t -> new CommissionSchemeService.TierInput(t.minimum(), t.maximum(), t.rate(), t.order()))
                .toList();
        CommissionScheme scheme = schemeService.create(request.name(), request.effectiveFrom(), request.effectiveTo(),
                permission, inputs, actor.currentId());
        return toSchemeResponse(scheme);
    }

    @PutMapping("/schemes/{id}")
    public SchemeResponse updateScheme(@PathVariable Long id, @RequestBody SchemeRequest request) {
        return toSchemeResponse(schemeService.update(id, request.name(), request.effectiveFrom(),
                request.effectiveTo(), actor.currentId()));
    }

    @PatchMapping("/schemes/{id}/activate")
    public void activateScheme(@PathVariable Long id) {
        schemeService.activate(id);
    }

    @DeleteMapping("/schemes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateScheme(@PathVariable Long id) {
        schemeService.deactivate(id);
    }

    @GetMapping("/report")
    public List<ReportRow> report(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (to.isBefore(from)) throw new IllegalArgumentException("invalid report date range");
        Map<Long, List<CourseSaleStaffShare>> byStaff = new LinkedHashMap<>();
        for (Course course : courses.findByReceiptSaleDateBetween(from, to)) {
            for (CourseSaleStaffShare share : saleShares.findByCourseId(course.getId())) {
                byStaff.computeIfAbsent(share.getStaff().getId(), k -> new ArrayList<>()).add(share);
            }
        }
        List<ReportRow> rows = new ArrayList<>();
        for (List<CourseSaleStaffShare> group : byStaff.values()) {
            Course first = group.get(0).getCourse();
            Staff seller = group.get(0).getStaff();
            BigDecimal base = group.stream().map(CourseSaleStaffShare::getCreditedSalesAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            CommissionScheme scheme = first.getCommissionScheme();
            CommissionTier tier = tierResolver.resolve(scheme, base);
            rows.add(new ReportRow(seller.getId(), seller.getStaffFname() + " " + seller.getStaffLname(),
                    first.getReceipt().getBranch().getBranchCode(), first.getReceipt().getBranch().getBranchName(),
                    group.size(), base, tier.getCommissionRate(), base.multiply(tier.getCommissionRate())));
        }
        return rows;
    }

    @GetMapping("/treatment-report")
    public List<TreatmentReportRow> treatmentReport(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (to.isBefore(from)) throw new IllegalArgumentException("invalid report date range");
        return allocationLedger.findByVisitDateBetweenOrderByVisitDateDescIdDesc(from, to).stream().map(row -> {
            Course course = row.getCourse();
            var patient = row.getPatient();
            Staff treating = row.getTreatingStaff();
            Staff owner = row.getCaseOwnerStaff();
            return new TreatmentReportRow(row.getId(), row.getVisitDate(), course.getId(), course.getCourseNo(),
                    patient.getId(), patient.getPatientHn(), patient.getPatientFname() + " " + patient.getPatientLname(),
                    course.getReceipt().getBranch().getBranchCode(), course.getReceipt().getBranch().getBranchName(),
                    treating == null ? null : treating.getId(),
                    treating == null ? "ไม่ระบุ" : treating.getStaffFname() + " " + treating.getStaffLname(),
                    owner == null ? null : owner.getId(),
                    owner == null ? "ไม่ระบุ" : owner.getStaffFname() + " " + owner.getStaffLname(),
                    row.getGrossCommissionAllocation(), row.getTreatmentFeeAmount(), row.getOwnerNetCommission());
        }).toList();
    }

    @GetMapping("/closings")
    public List<ClosingResponse> closings(@RequestParam String month) {
        String saleMonth = month.length() >= 7 ? month.substring(0, 7) : month;
        return closings.findByClosingMonth(saleMonth).stream().map(c -> new ClosingResponse(c.getId(),
                c.getClosingMonth(), c.getStaff().getId(), c.getStaff().getStaffFname() + " " + c.getStaff().getStaffLname(),
                c.getMonthlyCourseSales(), String.valueOf(c.getCommissionSchemeVersion()),
                c.getLockedCommissionRate() != null ? c.getLockedCommissionRate() : c.getCalculatedCommissionRate(),
                c.isClosingStatus() ? "CLOSED" : "OPEN")).toList();
    }

    @PostMapping("/closings/{month}")
    public List<ClosingResponse> closeMonth(@PathVariable String month) {
        String saleMonth = month.length() >= 7 ? month.substring(0, 7) : month;
        Permission permission = resolveOrCreatePermission("COMMISSION_CLOSING_MANAGE");
        for (Long sellerId : courses.findDistinctCreditedStaffIdsBySaleMonth(saleMonth)) {
            Staff seller = staff.findById(sellerId).orElseThrow();
            boolean alreadyClosed = closings.findByClosingMonthAndStaffId(saleMonth, sellerId)
                    .map(MonthlyCommissionClosing::isClosingStatus).orElse(false);
            if (!alreadyClosed) {
                closingService.closeMonth(saleMonth, seller, permission, actor.currentId());
            }
        }
        return closings(month);
    }

    public record OverrideRequest(BigDecimal rate, String reason) {}
    public record OverrideResponse(Long courseId, BigDecimal oldRate, BigDecimal newRate, String reason,
                                    BigDecimal commissionPool) {}

    /** Manually corrects the locked commission rate of one course (needs a reason; audited). */
    @PostMapping("/courses/{courseId}/override-rate")
    public OverrideResponse overrideRate(@PathVariable Long courseId, @RequestBody OverrideRequest request) {
        Course course = courses.findById(courseId).orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
        Permission permission = resolveOrCreatePermission("COMMISSION_RATE_OVERRIDE");
        var saved = overrideService.override(course, request.rate(), permission, request.reason(), actor.currentId());
        audit.record("course", String.valueOf(courseId), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                "commission rate " + saved.getOldRate(),
                "commission rate " + saved.getNewRate() + ": " + request.reason(), actor.currentId());
        return new OverrideResponse(courseId, saved.getOldRate(), saved.getNewRate(), saved.getReason(),
                courses.findById(courseId).orElseThrow().getTotalCourseCommissionPool());
    }

    @GetMapping("/treatment-fees")
    public List<TreatmentFeeResponse> treatmentFees() {
        return treatmentFeeRules.findAll().stream()
                .map(r -> new TreatmentFeeResponse(r.getId(), r.getStaff() == null ? null : r.getStaff().getId(),
                        r.getFeeType().name(), r.getFeeValue(),
                        r.getPercentageBase() == null ? null : r.getPercentageBase().name()))
                .toList();
    }

    private Permission resolveOrCreatePermission(String name) {
        return permissions.findAll().stream().filter(p -> name.equals(p.getPermissionName())).findFirst()
                .orElseGet(() -> {
                    Permission permission = new Permission();
                    permission.setPermissionName(name);
                    permission.setModuleName("Commission");
                    permission.setPermissionCreateAt(Instant.now());
                    permission.setPermissionCreateBy(actor.currentId());
                    return permissions.save(permission);
                });
    }

    private static SchemeResponse toSchemeResponse(CommissionScheme scheme) {
        return new SchemeResponse(scheme.getId(), scheme.getSchemeName(), scheme.getSchemeVersion(),
                scheme.getEffectiveFrom(), scheme.getEffectiveTo());
    }

    private static TierResponse toTierResponse(CommissionTier tier) {
        return new TierResponse(tier.getId(), tier.getTierOrder(), tier.getMinimumMonthlySales(),
                tier.getMaximumMonthlySales(), tier.getCommissionRate(), tier.isTierStatus());
    }
}
