package com.sabyeguyz.clinicv2.commission;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
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
    private final TreatmentFeeRuleService treatmentFeeService;
    private final com.sabyeguyz.clinicv2.course.CourseRefundItemRepository refundItems;
    private final com.sabyeguyz.clinicv2.sale.SingleVisitFeeAccrual singleVisitFeeAccrual;
    private final com.sabyeguyz.clinicv2.course.CourseVisitUsageRepository usageRepository;
    private final MonthlyTierSalesService tierSales;
    private final CommissionTierResolver tierResolver;
    private final CommissionSchemeRepository schemes;
    private final com.sabyeguyz.clinicv2.auth.AppUserRepository users;
    private final MonthlyCommissionClosingService closingService;
    private final CommissionSchemeResolver schemeResolver;
    private final MonthlyCommissionClosingRepository closings;
    private final CommissionAllocationLedgerRepository allocationLedger;
    private final TreatmentFeeRuleRepository treatmentFeeRules;
    private final CourseRepository courses;
    private final StaffRepository staff;
    private final PermissionRepository permissions;
    private final CurrentActor actor;
    private final CommissionRateOverrideService overrideService;
    private final com.sabyeguyz.clinicv2.audit.AuditLogService audit;

    public CommissionController(CommissionSchemeService schemeService, CommissionTierResolver tierResolver,
                                 CommissionSchemeRepository schemes, MonthlyCommissionClosingService closingService,
                                 CommissionSchemeResolver schemeResolver,
                                 MonthlyCommissionClosingRepository closings,
                                 CommissionAllocationLedgerRepository allocationLedger,
                                 TreatmentFeeRuleRepository treatmentFeeRules, CourseRepository courses,
                                 StaffRepository staff, PermissionRepository permissions, CurrentActor actor,
                                 CommissionRateOverrideService overrideService,
                                 com.sabyeguyz.clinicv2.audit.AuditLogService audit,
                                 TreatmentFeeRuleService treatmentFeeService, MonthlyTierSalesService tierSales,
                                 com.sabyeguyz.clinicv2.course.CourseVisitUsageRepository usageRepository,
                                 com.sabyeguyz.clinicv2.sale.SingleVisitFeeAccrual singleVisitFeeAccrual,
                                 com.sabyeguyz.clinicv2.course.CourseRefundItemRepository refundItems,
                                 com.sabyeguyz.clinicv2.auth.AppUserRepository users) {
        this.users = users;
        this.refundItems = refundItems;
        this.singleVisitFeeAccrual = singleVisitFeeAccrual;
        this.usageRepository = usageRepository;
        this.treatmentFeeService = treatmentFeeService;
        this.tierSales = tierSales;
        this.schemeService = schemeService;
        this.tierResolver = tierResolver;
        this.schemes = schemes;
        this.closingService = closingService;
        this.schemeResolver = schemeResolver;
        this.closings = closings;
        this.allocationLedger = allocationLedger;
        this.treatmentFeeRules = treatmentFeeRules;
        this.courses = courses;
        this.staff = staff;
        this.permissions = permissions;
        this.actor = actor;
        this.overrideService = overrideService;
        this.audit = audit;
    }

    public record TierInput(BigDecimal minimum, BigDecimal maximum, BigDecimal rate, int order) {}
    public record SchemeRequest(String name, String version, LocalDate effectiveFrom, LocalDate effectiveTo,
                                 List<TierInput> tiers) {}

    /** createdBy is the name of the user who recorded the version (history of the commission rules). */
    public record SchemeResponse(Long id, String name, int version, LocalDate effectiveFrom, LocalDate effectiveTo,
                                  boolean locked, String createdBy, java.time.Instant createdAt,
                                  java.time.Instant updatedAt, String updatedBy) {}
    public record TierResponse(Long id, int order, BigDecimal minimum, BigDecimal maximum, BigDecimal rate) {}
    public record ReportRow(Long staffId, String staffName, String branchCode, String branchName, int saleCount,
                             BigDecimal base, BigDecimal rate, BigDecimal commission) {}
    /** ownerNet and gross are null while the sale month is not closed (the Owner commission is not known yet). */
    public record TreatmentReportRow(Long id, LocalDate visitDate, Long coursePurchaseId, String courseCode,
                                      Long patientId, String patientHn, String patientName, String branchCode,
                                      String branchName, Long treatingStaffId, String treatingStaffName,
                                      Long ownerStaffId, String ownerStaffName, BigDecimal gross,
                                      BigDecimal treatmentFee, BigDecimal ownerNet, boolean ownerNetPending) {}
    public record ClosingResponse(Long id, String month, Long employeeId, String employeeName, BigDecimal sales,
                                   String version, BigDecimal rate, String status, String reopenReason,
                                   java.time.Instant reopenedAt) {}
    public record TreatmentFeeResponse(Long id, Long staffId, String feeType, BigDecimal feeValue, String percentageBase,
                                        LocalDate effectiveFrom, LocalDate effectiveTo, boolean enabled, boolean used,
                                        String name, String lastEditedBy, java.time.Instant lastEditedAt) {}
    public record TreatmentFeeRequest(String feeType, BigDecimal feeValue, LocalDate effectiveFrom, LocalDate effectiveTo,
                                       String name) {}

    @GetMapping("/schemes")
    public List<SchemeResponse> schemes() {
        return schemeService.list().stream().map(this::toSchemeResponse).toList();
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
        // the end of a version is set by the system (the day before the next one starts), never by the request
        CommissionScheme scheme = schemeService.create(request.name(), request.effectiveFrom(), null,
                permission, inputs, actor.currentId());
        audit.recordChange("commission_scheme", String.valueOf(scheme.getId()), com.sabyeguyz.clinicv2.enums.AuditAction.INSERT,
                null, schemeSnapshot(scheme), actor.currentId());
        return toSchemeResponse(scheme);
    }

    /** A scheme in use keeps its tiers: a request that carries tiers for it is refused with 409. */
    @PutMapping("/schemes/{id}")
    public SchemeResponse updateScheme(@PathVariable Long id, @RequestBody SchemeRequest request) {
        java.util.Map<String, Object> before = schemes.findById(id).map(this::schemeSnapshot).orElse(null);
        CommissionScheme scheme = schemeService.update(id, request.name(), request.effectiveFrom(),
                null, toInputs(request.tiers()), actor.currentId());
        audit.recordChange("commission_scheme", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                before, schemeSnapshot(scheme), actor.currentId());
        return toSchemeResponse(scheme);
    }

    /** New version of the scheme (same name) with new tiers; it takes over from {@code effectiveFrom}. */
    @PostMapping("/schemes/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public SchemeResponse createSchemeVersion(@PathVariable Long id, @RequestBody SchemeRequest request) {
        CommissionScheme scheme = schemeService.createVersion(id, request.effectiveFrom(), null,
                toInputs(request.tiers()), actor.currentId());
        audit.recordChange("commission_scheme", String.valueOf(scheme.getId()), com.sabyeguyz.clinicv2.enums.AuditAction.INSERT,
                com.sabyeguyz.clinicv2.audit.AuditLogService.fields("from_scheme_id", id), schemeSnapshot(scheme), actor.currentId());
        return toSchemeResponse(scheme);
    }

    private static List<CommissionSchemeService.TierInput> toInputs(List<TierInput> tiers) {
        return tiers == null ? null : tiers.stream()
                .map(t -> new CommissionSchemeService.TierInput(t.minimum(), t.maximum(), t.rate(), t.order())).toList();
    }

    @GetMapping("/report")
    public List<ReportRow> report(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (to.isBefore(from)) throw new IllegalArgumentException("invalid report date range");
        // A course sale is credited entirely to its seller (the Owner PT).
        Map<Long, List<Course>> bySeller = new LinkedHashMap<>();
        // only sales still in effect count, less the Completed refunds against them (the same base as the monthly tier)
        for (Course course : courses.findValidByReceiptSaleDateBetween(from, to)) {
            bySeller.computeIfAbsent(course.getSellerStaff().getId(), k -> new ArrayList<>()).add(course);
        }
        List<ReportRow> rows = new ArrayList<>();
        for (List<Course> group : bySeller.values()) {
            Course first = group.get(0);
            Staff seller = first.getSellerStaff();
            BigDecimal refunded = group.stream().map(course -> refundItems.sumRefundAmountOfCourse(course.getId(),
                    java.util.List.of(com.sabyeguyz.clinicv2.enums.RefundStatus.Completed)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal base = group.stream().map(Course::getCoursePrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).subtract(refunded).max(BigDecimal.ZERO);
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
        return usageRepository.findCompletedBetween(from, to).stream().map(row -> {
            Course course = row.getCourse();
            var patient = row.getPatient();
            Staff treating = row.getTreatingStaff();
            Staff owner = course.getOwnerStaff();
            BigDecimal ownerNet = row.getOwnerNetCommission();
            BigDecimal fee = row.getSubstituteTreatmentFeeAmount();
            return new TreatmentReportRow(row.getId(), row.getVisitDate().toLocalDate(), course.getId(), course.getCourseNo(),
                    patient.getId(), patient.getPatientHn(), patient.getPatientFname() + " " + patient.getPatientLname(),
                    course.getReceipt().getBranch().getBranchCode(), course.getReceipt().getBranch().getBranchName(),
                    treating == null ? null : treating.getId(),
                    treating == null ? "ไม่ระบุ" : treating.getStaffFname() + " " + treating.getStaffLname(),
                    owner == null ? null : owner.getId(),
                    owner == null ? "ไม่ระบุ" : owner.getStaffFname() + " " + owner.getStaffLname(),
                    ownerNet == null ? null : ownerNet.add(fee), fee, ownerNet, ownerNet == null);
        }).toList();
    }

    /**
     * The closings of a month: the ones already closed, plus an OPEN preview row (sales so far and the tier rate
     * they reach today) for every seller who sold a course that month and is not closed yet, so the screen can
     * offer "close the month".
     */
    @GetMapping("/closings")
    public List<ClosingResponse> closings(@RequestParam String month) {
        String saleMonth = month.length() >= 7 ? month.substring(0, 7) : month;
        List<ClosingResponse> rows = new ArrayList<>(closings.findByClosingMonth(saleMonth).stream().map(c -> new ClosingResponse(c.getId(),
                c.getClosingMonth(), c.getStaff().getId(), c.getStaff().getStaffFname() + " " + c.getStaff().getStaffLname(),
                c.getMonthlyCourseSales(), String.valueOf(c.getCommissionSchemeVersion()),
                c.getLockedCommissionRate() != null ? c.getLockedCommissionRate() : c.getCalculatedCommissionRate(),
                c.getClosingStatus().name().toUpperCase(java.util.Locale.ROOT), c.getReopenReason(), c.getReopenedAt())).toList());
        if (saleMonth.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            java.util.Set<Long> listed = new java.util.HashSet<>();
            rows.forEach(r -> listed.add(r.employeeId()));
            for (Long sellerId : courses.findDistinctCreditedStaffIdsBySaleMonth(saleMonth)) {
                if (listed.contains(sellerId)) continue;
                Staff seller = staff.findById(sellerId).orElse(null);
                if (seller == null) continue;
                BigDecimal sales = tierSales.netSales(sellerId, saleMonth);
                BigDecimal rate;
                try {
                    rate = tierResolver.resolve(schemeResolver.resolveEffective(LocalDate.parse(saleMonth + "-01")), sales)
                            .getCommissionRate();
                } catch (RuntimeException noScheme) {
                    rate = BigDecimal.ZERO;
                }
                rows.add(new ClosingResponse(-sellerId, saleMonth, sellerId,
                        seller.getStaffFname() + " " + seller.getStaffLname(), sales, "", rate, "OPEN", null, null));
            }
        }
        return rows;
    }

    @PostMapping("/closings/{month}")
    public List<ClosingResponse> closeMonth(@PathVariable String month) {
        String saleMonth = month.length() >= 7 ? month.substring(0, 7) : month;
        Permission permission = resolveOrCreatePermission("COMMISSION_CLOSING_MANAGE");
        for (Long sellerId : courses.findDistinctCreditedStaffIdsBySaleMonth(saleMonth)) {
            Staff seller = staff.findById(sellerId).orElseThrow();
            boolean alreadyClosed = closings.findByClosingMonthAndStaffId(saleMonth, sellerId)
                    .map(MonthlyCommissionClosing::isClosed).orElse(false);
            if (!alreadyClosed) {
                MonthlyCommissionClosing closed = closingService.closeMonth(saleMonth, seller, permission, actor.currentId());
                audit.recordChange("monthly_commission_closing", String.valueOf(closed.getId()), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                        com.sabyeguyz.clinicv2.audit.AuditLogService.fields("closing_status", "Open"),
                        com.sabyeguyz.clinicv2.audit.AuditLogService.fields("closing_status", "Closed",
                                "closing_month", saleMonth, "staff_id", sellerId,
                                "monthly_course_sales", closed.getMonthlyCourseSales(),
                                "calculated_commission_rate", closed.getCalculatedCommissionRate(),
                                "locked_commission_rate", closed.getLockedCommissionRate()), actor.currentId());
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
        audit.recordChange("course", String.valueOf(courseId), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                com.sabyeguyz.clinicv2.audit.AuditLogService.fields("locked_commission_rate", saved.getOldRate()),
                com.sabyeguyz.clinicv2.audit.AuditLogService.fields("locked_commission_rate", saved.getNewRate(),
                        "reason", request.reason()), actor.currentId());
        return new OverrideResponse(courseId, saved.getOldRate(), saved.getNewRate(), saved.getReason(),
                courses.findById(courseId).orElseThrow().getTotalCourseCommissionPool());
    }

    /** How the pay is worked out: the tiers and the treatment fee in force today, read only, for anyone signed in. */
    public record Formula(LocalDate asOf, SchemeResponse scheme, List<TierResponse> tiers, TreatmentFeeResponse treatmentFee) {}

    @GetMapping("/formula")
    @PreAuthorize("isAuthenticated()")
    public Formula formula() {
        LocalDate today = com.sabyeguyz.clinicv2.config.ClinicTime.today();
        SchemeResponse scheme = null;
        List<TierResponse> tierRows = List.of();
        try {
            CommissionScheme effective = schemeResolver.resolveEffective(today);
            scheme = toSchemeResponse(effective);
            tierRows = schemeService.tiersOf(effective.getId()).stream()
                    .map(CommissionController::toTierResponse).toList();
        } catch (RuntimeException noScheme) {
            // nothing in force: the page says so
        }
        TreatmentFeeResponse fee = treatmentFeeService.list().stream()
                .filter(rule -> rule.isTreatmentFeeRuleStatus() && !rule.getEffectiveFrom().isAfter(today)
                        && (rule.getEffectiveTo() == null || !rule.getEffectiveTo().isBefore(today)))
                .max(java.util.Comparator.comparing(TreatmentFeeRule::getEffectiveFrom))
                .map(this::toTreatmentFeeResponse).orElse(null);
        return new Formula(today, scheme, tierRows, fee);
    }

    @GetMapping("/treatment-fees")
    public List<TreatmentFeeResponse> treatmentFees() {
        return treatmentFeeService.list().stream().map(this::toTreatmentFeeResponse).toList();
    }

    @PostMapping("/treatment-fees")
    @ResponseStatus(HttpStatus.CREATED)
    public TreatmentFeeResponse createTreatmentFee(@RequestBody TreatmentFeeRequest request) {
        // the end of a rule is set by the system (the day before the next rule starts), never by the request
        TreatmentFeeRule rule = treatmentFeeService.create(parseFeeType(request.feeType()), request.feeValue(),
                request.effectiveFrom(), null, resolveOrCreatePermission("COMMISSION_SCHEME_MANAGE"),
                actor.currentId());
        if (request.name() != null) {
            rule = treatmentFeeService.rename(rule.getId(), request.name(), actor.currentId());
        }
        audit.recordChange("treatment_fee_rule", String.valueOf(rule.getId()), com.sabyeguyz.clinicv2.enums.AuditAction.INSERT,
                null, feeSnapshot(rule), actor.currentId());
        return toTreatmentFeeResponse(rule);
    }

    @PutMapping("/treatment-fees/{id}")
    public TreatmentFeeResponse updateTreatmentFee(@PathVariable Long id, @RequestBody TreatmentFeeRequest request) {
        java.util.Map<String, Object> before = treatmentFeeRules.findById(id).map(this::feeSnapshot).orElse(null);
        TreatmentFeeRule rule = treatmentFeeService.update(id,
                request.feeType() == null ? null : parseFeeType(request.feeType()), request.feeValue(),
                request.effectiveFrom(), null, actor.currentId());
        if (request.name() != null) {
            rule = treatmentFeeService.rename(id, request.name(), actor.currentId());
        }
        audit.recordChange("treatment_fee_rule", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                before, feeSnapshot(rule), actor.currentId());
        return toTreatmentFeeResponse(rule);
    }

    /** Records the single-visit fees still missing for paid visits of the period (after a rule was set). */
    @PostMapping("/single-visit-fees/backfill")
    public java.util.Map<String, Integer> backfillSingleVisitFees(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (to.isBefore(from)) throw new IllegalArgumentException("invalid date range");
        int recorded = singleVisitFeeAccrual.backfill(from, to, actor.currentId());
        if (recorded > 0) {
            audit.recordChange("single_visit_fee_ledger", from + ".." + to, com.sabyeguyz.clinicv2.enums.AuditAction.INSERT, null,
                    com.sabyeguyz.clinicv2.audit.AuditLogService.fields("from", from, "to", to, "recorded", recorded), actor.currentId());
        }
        return java.util.Map.of("recorded", recorded);
    }

    @PatchMapping("/treatment-fees/{id}/enable")
    public TreatmentFeeResponse enableTreatmentFee(@PathVariable Long id) {
        return setTreatmentFeeEnabled(id, true);
    }

    @PatchMapping("/treatment-fees/{id}/disable")
    public TreatmentFeeResponse disableTreatmentFee(@PathVariable Long id) {
        return setTreatmentFeeEnabled(id, false);
    }

    private TreatmentFeeResponse setTreatmentFeeEnabled(Long id, boolean enabled) {
        java.util.Map<String, Object> before = treatmentFeeRules.findById(id).map(this::feeSnapshot).orElse(null);
        TreatmentFeeRule rule = treatmentFeeService.setEnabled(id, enabled, actor.currentId());
        java.util.Map<String, Object> after = feeSnapshot(rule);
        if (before == null || !before.equals(after)) {
            audit.recordChange("treatment_fee_rule", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                    before, after, actor.currentId());
        }
        return toTreatmentFeeResponse(rule);
    }

    /** Before/after value of a scheme for Audit_log: the version, its months and its tiers. */
    private java.util.Map<String, Object> schemeSnapshot(CommissionScheme scheme) {
        java.util.List<java.util.Map<String, Object>> tierRows = schemeService.tiersOf(scheme.getId()).stream()
                .map(t -> com.sabyeguyz.clinicv2.audit.AuditLogService.fields("tier_order", t.getTierOrder(),
                        "minimum_monthly_sales", t.getMinimumMonthlySales(), "maximum_monthly_sales",
                        t.getMaximumMonthlySales(), "commission_rate", t.getCommissionRate()))
                .toList();
        return com.sabyeguyz.clinicv2.audit.AuditLogService.fields("scheme_name", scheme.getSchemeName(),
                "scheme_version", scheme.getSchemeVersion(), "effective_from", scheme.getEffectiveFrom(),
                "effective_to", scheme.getEffectiveTo(), "tiers", tierRows);
    }

    private java.util.Map<String, Object> feeSnapshot(TreatmentFeeRule rule) {
        return com.sabyeguyz.clinicv2.audit.AuditLogService.fields("rule_name", rule.getRuleName(),
                "fee_type", rule.getFeeType(), "fee_value", rule.getFeeValue(), "effective_from", rule.getEffectiveFrom(),
                "effective_to", rule.getEffectiveTo(), "enabled", rule.isTreatmentFeeRuleStatus());
    }

    private static com.sabyeguyz.clinicv2.enums.FeeType parseFeeType(String value) {
        try {
            return com.sabyeguyz.clinicv2.enums.FeeType.valueOf(value == null ? "" : value.trim());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("feeType ต้องเป็น Fixed หรือ Percentage");
        }
    }

    private TreatmentFeeResponse toTreatmentFeeResponse(TreatmentFeeRule rule) {
        // staffId / percentageBase stay in the response for the screens but are always null: one clinic-wide rule
        return new TreatmentFeeResponse(rule.getId(), null, rule.getFeeType().name(), rule.getFeeValue(), null,
                rule.getEffectiveFrom(), rule.getEffectiveTo(), rule.isTreatmentFeeRuleStatus(),
                treatmentFeeService.isUsed(rule.getId()), rule.getRuleName(),
                recorderName(rule.getTreatmentFeeRuleUpdateBy() != null ? rule.getTreatmentFeeRuleUpdateBy()
                        : rule.getTreatmentFeeRuleCreateBy()),
                rule.getTreatmentFeeRuleUpdateAt() != null ? rule.getTreatmentFeeRuleUpdateAt()
                        : rule.getTreatmentFeeRuleCreateAt());
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

    private SchemeResponse toSchemeResponse(CommissionScheme scheme) {
        return new SchemeResponse(scheme.getId(), scheme.getSchemeName(), scheme.getSchemeVersion(),
                scheme.getEffectiveFrom(), scheme.getEffectiveTo(), schemeService.isReferenced(scheme.getId()),
                recorderName(scheme.getSchemeCreateBy()), scheme.getSchemeCreateAt(), scheme.getSchemeUpdateAt(),
                recorderName(scheme.getSchemeUpdateBy()));
    }

    private String recorderName(Long userId) {
        if (userId == null) return null;
        return users.findById(userId)
                .map(user -> user.getStaff() != null
                        ? user.getStaff().getStaffFname() + " " + user.getStaff().getStaffLname()
                        : user.getUsername())
                .orElse(null);
    }

    private static TierResponse toTierResponse(CommissionTier tier) {
        return new TierResponse(tier.getId(), tier.getTierOrder(), tier.getMinimumMonthlySales(),
                tier.getMaximumMonthlySales(), tier.getCommissionRate());
    }
}
