package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.appointment.PatientVisitRepository;
import com.sabyeguyz.clinicv2.appointment.PatientVisitService;
import com.sabyeguyz.clinicv2.auth.AppUserRepository;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.catalog.ServiceRepository;
import com.sabyeguyz.clinicv2.commission.CommissionAllocationLedger;
import com.sabyeguyz.clinicv2.commission.CommissionAllocationLedgerRepository;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.enums.CourseStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.patient.PatientRepository;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.role.PermissionService;
import com.sabyeguyz.clinicv2.sale.Discount;
import com.sabyeguyz.clinicv2.sale.Receipt;
import com.sabyeguyz.clinicv2.sale.ReceiptService;
import com.sabyeguyz.clinicv2.sale.SalesTransactionService;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's courseApi's sold-instance endpoints
 * (POST /api/course-purchases, GET /api/course-purchases/{id}, POST
 * /api/course-usages, POST /api/course-movements/{id}/reverse, POST
 * /api/course-purchases/{id}/cancel, GET /api/patients/{id}/courses). A
 * "course purchase" here is v2's own {@link Course} entity (the sold
 * instance), keyed by course_id.
 *
 * <p>Real gaps vs. the old API, all flagged for the team:</p>
 * <ul>
 *   <li>{@code purchase}: the old body has no seller field, so this resolves
 *   the seller from the current user's linked Staff record and fails if
 *   the logged-in account has none - the frontend form should really
 *   collect a seller explicitly.</li>
 *   <li>{@code use}: the PT who treats the session is {@code treatingStaffId} (the Owner PT by
 *   default); a different PT is a substitute who earns the treatment fee. The using patient is
 *   {@code patientId} (the Course's buyer, or the appointment's patient, by default).</li>
 *   <li>{@code use}: Course_visit_usage.visit_id is NOT NULL, so a usage always
 *   belongs to a visit. With an appointmentId it uses that appointment's
 *   checked-in visit; without one (what the frontend sends today) it records a
 *   walk-in visit for the using patient, checked in and completed at the treating PT.</li>
 *   <li>{@code reverse}: reverses the usage (Treatment movement) or the
 *   transfer (TransferOut/TransferIn movement) the given ledger movement
 *   belongs to, with proper Reversal ledger entries, a reversed commission
 *   allocation and a cancellation record. Other movement types (Purchase,
 *   Adjustment, Reversal) cannot be reversed here.</li>
 *   <li>{@code cancel}: voids the sale line of the course (a Void cancellation), stops every right not yet
 *   used with a Cancellation ledger entry and marks the course Cancelled. Sessions already given keep their
 *   treatment fee and commission.</li>
 * </ul>
 */
@Transactional
@RestController
public class CoursePurchaseController {
    private final CourseRepository courses;
    private final CourseLedgerRepository ledger;
    private final CourseVisitUsageRepository usages;
    private final CourseVisitUsageService usageService;
    private final CourseTransferService transferService;
    private final PermissionService permissions;
    private final CommissionAllocationLedgerRepository allocationLedger;
    private final ReceiptService receiptService;
    private final SalesTransactionService salesTransactionService;
    private final PatientRepository patients;
    private final BranchRepository branches;
    private final ServiceRepository services;
    private final PatientVisitRepository visits;
    private final PatientVisitService visitService;
    private final AppUserRepository users;
    private final com.sabyeguyz.clinicv2.staff.StaffRepository staffRepository;
    private final CurrentActor actor;
    private final com.sabyeguyz.clinicv2.audit.AuditLogService audit;
    private final com.sabyeguyz.clinicv2.sale.TransactionCancellationService cancellations;

    public CoursePurchaseController(CourseRepository courses, CourseLedgerRepository ledger,
                                     CourseVisitUsageRepository usages, CourseVisitUsageService usageService,
                                     CourseTransferService transferService, PermissionService permissions,
                                     CommissionAllocationLedgerRepository allocationLedger, ReceiptService receiptService,
                                     SalesTransactionService salesTransactionService,
                                     PatientRepository patients, BranchRepository branches, ServiceRepository services,
                                     PatientVisitRepository visits, PatientVisitService visitService,
                                     AppUserRepository users, com.sabyeguyz.clinicv2.staff.StaffRepository staffRepository,
                                     CurrentActor actor,
                                     com.sabyeguyz.clinicv2.audit.AuditLogService audit,
                                     com.sabyeguyz.clinicv2.sale.TransactionCancellationService cancellations) {
        this.cancellations = cancellations;
        this.courses = courses;
        this.ledger = ledger;
        this.usages = usages;
        this.usageService = usageService;
        this.transferService = transferService;
        this.permissions = permissions;
        this.allocationLedger = allocationLedger;
        this.receiptService = receiptService;
        this.salesTransactionService = salesTransactionService;
        this.patients = patients;
        this.branches = branches;
        this.services = services;
        this.visits = visits;
        this.visitService = visitService;
        this.users = users;
        this.staffRepository = staffRepository;
        this.actor = actor;
        this.audit = audit;
    }

    /** A sale credit split: {@code percent} of the course price is credited to {@code staffId}. */
    /** Sale-credit shares were removed: a course has a single Owner PT, its seller. */
    public record ShareRequest(Long staffId, BigDecimal percent) {}
    /**
     * {@code shares} (optional) splits the sale credit between up to two PTs and must add up to 100%;
     * without it the seller (the signed-in user's staff record) gets 100%. {@code caseOwnerStaffId}
     * (optional) names the PT who owns the course's commission, the seller by default.
     */
    public record PurchaseRequest(Long patientId, Long courseId, String branchCode, String discountType,
                                   BigDecimal discountValue, Long caseOwnerStaffId, List<ShareRequest> shares) {}
    /** treatingStaffId (optional): the PT who treats the session, the Owner PT by default; patientId (optional): who uses the entitlement. */
    public record UseRequest(Long coursePurchaseId, Long appointmentId, String note, Long treatingStaffId, Long patientId) {}
    public record ReasonRequest(String reason) {}

    public record PatientSummary(Long id, String hn, String fullName) {}
    public record CourseSummary(Long id, String code, String name) {}

    public record Response(Long id, PatientSummary patient, String branchCode, CourseSummary course,
                            BigDecimal price, int sessions, int bonusSessions, int purchased, int used, int remaining,
                            Instant purchasedAt, Instant expiresAt, boolean expired, boolean usable, String status,
                            String commissionStatus, BigDecimal lockedCommissionRate,
                            BigDecimal totalCourseCommissionPool, BigDecimal commissionAllocationPerVisit) {}

    public record MovementResponse(Long id, String type, int quantity, String reason, Instant createdAt) {}
    public record AllocationResponse(LocalDate visitDate, BigDecimal gross, BigDecimal fee, BigDecimal ownerNet) {}
    public record LedgerResponse(Response purchase, List<MovementResponse> movements, List<AllocationResponse> allocations) {}

    @GetMapping("/api/patients/{patientId}/courses")
    public List<Response> patientCourses(@PathVariable Long patientId) {
        return courses.findByPatientId(patientId).stream().map(c -> toResponse(c, patientId)).toList();
    }

    @PostMapping("/api/course-purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public Response purchase(@RequestBody PurchaseRequest request) {
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"));
        Branch branch = branches.findByBranchCode(request.branchCode().trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + request.branchCode()));
        Service courseTemplate = services.findById(request.courseId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
        if (request.shares() != null && !request.shares().isEmpty()) {
            throw new IllegalArgumentException("ไม่รองรับการแบ่งเครดิตยอดขายแล้ว เจ้าของคอร์สคือผู้ขายคนเดียว");
        }
        // The seller is the Owner PT. A manager may record the sale for a PT with caseOwnerStaffId; the signed-in
        // user's own staff record is the default.
        Staff seller = request.caseOwnerStaffId() == null ? resolveActorStaff() : staffById(request.caseOwnerStaffId());

        Receipt receipt = receiptService.create(receiptService.nextReceiptNo(branch, LocalDate.now()), patient, branch, LocalDate.now(),
                actor.currentId());
        var purchase = salesTransactionService.recordCoursePurchase(receipt, patient, branch, courseTemplate,
                seller, null, null, null, Discount.of(request.discountType(), request.discountValue()),
                LocalDate.now(), actor.currentId());
        receiptService.finalizeTotals(receipt, List.of(purchase.transaction()), Discount.none(), actor.currentId());
        audit.record("course", String.valueOf(purchase.course().getId()), com.sabyeguyz.clinicv2.enums.AuditAction.INSERT, null,
                purchase.course().getCourseNo() + " price " + purchase.course().getCoursePrice(), actor.currentId());
        return toResponse(purchase.course(), patient.getId());
    }

    @GetMapping("/api/course-purchases/{id}")
    public LedgerResponse ledger(@PathVariable Long id) {
        Course course = findCourse(id);
        Long ownerPatientId = course.getPurchaserPatient().getId();
        List<MovementResponse> movements = ledger.findByCourseIdOrderByIdDesc(id).stream()
                .map(l -> new MovementResponse(l.getId(), apiMovementType(l.getEntryType()), l.getQtyChange(), l.getRemark(),
                        l.getLedgerCreateAt()))
                .toList();
        List<AllocationResponse> allocations = allocationLedger.findByCourseIdOrderByIdDesc(id).stream()
                .map(a -> new AllocationResponse(a.getVisitDate(), a.getOwnerNetCommission().add(a.getTreatmentFeeAmount()),
                        a.getTreatmentFeeAmount(), a.getOwnerNetCommission()))
                .toList();
        return new LedgerResponse(toResponse(course, ownerPatientId), movements, allocations);
    }

    public record UsageTransactionResponse(Long id, Long coursePurchaseId, String patientName, String patientHn,
                                            String courseName, String branchCode, Instant usedAt, Instant voidedAt,
                                            String voidReason) {}

    /** Every visit taken from a course (newest first) with its reversal status; id is the Treatment movement. */
    @GetMapping("/api/course-usages")
    public List<UsageTransactionResponse> usageTransactions() {
        return usages.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "id")).stream().map(usage -> {
            List<com.sabyeguyz.clinicv2.course.CourseLedger> movements = ledger.findByVisitUsageId(usage.getId());
            Long movementId = movements.stream().filter(l -> l.getEntryType() == CourseLedgerEntryType.Treatment)
                    .map(com.sabyeguyz.clinicv2.course.CourseLedger::getId).findFirst().orElse(usage.getId());
            Course course = usage.getCourse();
            // The reversal row points at the Treatment row it undoes (reverses_ledger), not at the usage.
            var reversal = ledger.findByCourseIdOrderByIdDesc(course.getId()).stream()
                    .filter(l -> l.getEntryType() == CourseLedgerEntryType.Reversal && l.getReversesLedger() != null
                            && l.getReversesLedger().getId().equals(movementId))
                    .findFirst();
            Patient patient = usage.getPatient();
            return new UsageTransactionResponse(movementId, course.getId(),
                    patient.getPatientFname() + " " + patient.getPatientLname(), patient.getPatientHn(),
                    course.getCourseName(), course.getReceipt().getBranch().getBranchCode(),
                    usage.getUsageCreateAt(), reversal.map(l -> l.getLedgerCreateAt()).orElse(null),
                    reversal.map(l -> l.getRemark()).orElse(null));
        }).toList();
    }

    @PostMapping("/api/course-usages")
    @Transactional
    public Response use(@RequestBody UseRequest request) {
        // Row-lock first: two parallel usages must not both see the last remaining visit.
        Course course = courses.findByIdForUpdate(request.coursePurchaseId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
        Staff treating = request.treatingStaffId() == null ? course.getOwnerStaff() : staffById(request.treatingStaffId());
        PatientVisit appointmentVisit = request.appointmentId() == null ? null
                : visits.findFirstByAppointmentIdOrderByIdDesc(request.appointmentId())
                        .orElseThrow(() -> new IllegalArgumentException("ยังไม่มีการเช็คอินสำหรับนัดหมายนี้"));
        Patient usingPatient = request.patientId() != null
                ? patients.findById(request.patientId()).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"))
                : appointmentVisit != null ? appointmentVisit.getPatient() : course.getPurchaserPatient();
        if (appointmentVisit != null && !appointmentVisit.getPatient().getId().equals(usingPatient.getId())) {
            throw new IllegalArgumentException("ผู้ใช้สิทธิ์ต้องเป็นผู้ป่วยคนเดียวกับของ Visit");
        }
        PatientVisit visit = appointmentVisit != null ? appointmentVisit : walkInVisit(course, usingPatient, treating);
        usageService.use(course, visit, usingPatient, treating, LocalDate.now(), actor.currentId());
        return toResponse(courses.findById(course.getId()).orElseThrow(), usingPatient.getId());
    }

    @PostMapping("/api/course-movements/{movementId}/reverse")
    public Response reverse(@PathVariable Long movementId, @RequestBody ReasonRequest request) {
        CourseLedger original = ledger.findById(movementId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการ"));
        Permission permission = permissions.getOrCreate("COURSE_REVERSE", "Course", actor.currentId());
        switch (original.getEntryType()) {
            case Treatment -> usageService.reverse(original.getVisitUsage(), permission, request.reason(), actor.currentId());
            case TransferOut, TransferIn ->
                    transferService.reverse(original.getTransfer(), permission, request.reason(), actor.currentId());
            default -> throw new IllegalArgumentException("ไม่รองรับการย้อนรายการประเภท " + original.getEntryType());
        }
        Course course = findCourse(original.getCourse().getId());
        return toResponse(course, original.getPatient().getId());
    }

    @PostMapping("/api/course-purchases/{id}/cancel")
    public Response cancel(@PathVariable Long id, @RequestBody ReasonRequest request) {
        Course course = courses.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
        if (course.getCourseStatus() != CourseStatus.Active && course.getCourseStatus() != CourseStatus.Completed) {
            throw new IllegalStateException("คอร์สนี้ถูกยกเลิกหรือคืนเงินไปแล้ว");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new IllegalArgumentException("ต้องระบุเหตุผลที่ยกเลิก");
        }
        Permission permission = permissions.getOrCreate("SALE_CANCEL", "Sale", actor.currentId());
        // voids the sale line, cuts the unused rights and marks the course Cancelled
        cancellations.voidSale(course.getSaleTransaction(), permission, request.reason(), actor.currentId());
        Course saved = findCourse(id);
        if (saved.getCourseStatus() != CourseStatus.Cancelled) {
            saved.setCourseStatus(CourseStatus.Cancelled); // every right was already used: nothing to cut, but the sale is void
            saved.setCourseUpdateAt(Instant.now());
            saved.setCourseUpdateBy(actor.currentId());
            saved = courses.save(saved);
        }
        audit.record("course", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE, "Active", "Cancelled: " + request.reason(),
                actor.currentId());
        return toResponse(saved, saved.getPurchaserPatient().getId());
    }

    private PatientVisit walkInVisit(Course course, Patient usingPatient, Staff treating) {
        PatientVisit visit = visitService.checkInWalkIn(usingPatient, course.getReceipt().getBranch(),
                treating, actor.currentId());
        return visitService.complete(visit.getId(), actor.currentId());
    }

    private static String apiMovementType(CourseLedgerEntryType type) {
        return switch (type) {
            case Treatment -> "USAGE";
            case TransferOut -> "TRANSFER_OUT";
            case TransferIn -> "TRANSFER_IN";
            default -> type.name().toUpperCase(java.util.Locale.ROOT);
        };
    }

    private Staff staffById(Long id) {
        return staffRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบพนักงาน"));
    }

    private Staff resolveActorStaff() {
        Long userId = actor.requireId();
        var user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ใช้"));
        if (user.getStaff() == null) {
            throw new IllegalArgumentException("บัญชีผู้ใช้นี้ไม่ได้ผูกกับข้อมูลพนักงาน ไม่สามารถเป็นผู้ขายได้");
        }
        return user.getStaff();
    }

    private Course findCourse(Long id) {
        return courses.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบคอร์ส"));
    }

    private Response toResponse(Course course, Long forPatientId) {
        long remaining = forPatientId == null ? course.getRemainingVisits()
                : ledger.sumQtyChangeByCourseAndPatient(course.getId(), forPatientId);
        boolean expired = course.getExpiryDate().isBefore(LocalDate.now());
        boolean usable = !expired && course.getCourseStatus() == CourseStatus.Active && remaining > 0;
        Patient patient = course.getPurchaserPatient();
        return new Response(course.getId(), new PatientSummary(patient.getId(), patient.getPatientHn(),
                patient.getPatientFname() + " " + patient.getPatientLname()),
                course.getReceipt().getBranch().getBranchCode(),
                new CourseSummary(course.getService().getId(), course.getCourseNo(), course.getCourseName()),
                course.getCoursePrice(), course.getPaidVisits(), course.getBonusVisits() == null ? 0 : course.getBonusVisits(),
                course.getTotalVisits(),
                course.getVisitsUsed(), (int) remaining,
                course.getCourseCreateAt(), course.getExpiryDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                expired, usable, course.getCourseStatus().name().toUpperCase(java.util.Locale.ROOT), course.getCommissionStatus().name().toUpperCase(java.util.Locale.ROOT),
                course.getLockedCommissionRate(), course.getTotalCourseCommissionPool(),
                course.getCommissionAllocationPerVisit());
    }
}
