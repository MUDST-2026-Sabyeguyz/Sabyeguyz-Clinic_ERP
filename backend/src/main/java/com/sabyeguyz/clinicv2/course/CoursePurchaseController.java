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
 *   <li>{@code use}: no treating-staff field either, so this always treats
 *   the Case Owner as the Treating PT (substitute-PT usage isn't reachable
 *   through this endpoint); the "using patient" for a Shared Course is
 *   likewise always the Course's original buyer, never a shared member.</li>
 *   <li>{@code use}: Course_visit_usage.visit_id is NOT NULL, so a usage always
 *   belongs to a visit. With an appointmentId it uses that appointment's
 *   checked-in visit; without one (what the frontend sends today) it records a
 *   walk-in visit for the buyer, checked in and completed at the Case Owner.</li>
 *   <li>{@code reverse}: reverses the usage (Treatment movement) or the
 *   transfer (TransferOut/TransferIn movement) the given ledger movement
 *   belongs to, with proper Reversal ledger entries, a reversed commission
 *   allocation and a cancellation record. Other movement types (Purchase,
 *   Adjustment, Reversal) cannot be reversed here.</li>
 *   <li>{@code cancel}: only flips course_status to Cancelled. The
 *   Commission_System_Requirement_Dev §22 Refund/Cancellation rules
 *   (stop future allocation, compute adjustment, audit trail) are not
 *   implemented.</li>
 * </ul>
 */
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
    private final CurrentActor actor;

    public CoursePurchaseController(CourseRepository courses, CourseLedgerRepository ledger,
                                     CourseVisitUsageRepository usages, CourseVisitUsageService usageService,
                                     CourseTransferService transferService, PermissionService permissions,
                                     CommissionAllocationLedgerRepository allocationLedger, ReceiptService receiptService,
                                     SalesTransactionService salesTransactionService,
                                     PatientRepository patients, BranchRepository branches, ServiceRepository services,
                                     PatientVisitRepository visits, PatientVisitService visitService,
                                     AppUserRepository users, CurrentActor actor) {
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
        this.actor = actor;
    }

    public record PurchaseRequest(Long patientId, Long courseId, String branchCode, String discountType,
                                   BigDecimal discountValue) {}
    public record UseRequest(Long coursePurchaseId, Long appointmentId, String note) {}
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
        Staff seller = resolveActorStaff();

        Receipt receipt = receiptService.create(receiptService.nextReceiptNo(), patient, branch, LocalDate.now(),
                actor.currentId());
        var purchase = salesTransactionService.recordCoursePurchase(receipt, patient, branch, courseTemplate,
                seller, null, null, null, null, Discount.of(request.discountType(), request.discountValue()), null,
                LocalDate.now(), actor.currentId());
        receiptService.finalizeTotals(receipt, List.of(purchase.transaction()), Discount.none(), actor.currentId());
        return toResponse(purchase.course(), patient.getId());
    }

    @GetMapping("/api/course-purchases/{id}")
    public LedgerResponse ledger(@PathVariable Long id) {
        Course course = findCourse(id);
        Long ownerPatientId = course.getPurchaserPatient().getId();
        List<MovementResponse> movements = ledger.findByCourseIdOrderByIdDesc(id).stream()
                .map(l -> new MovementResponse(l.getId(), l.getEntryType().name(), l.getQtyChange(), l.getRemark(),
                        l.getLedgerCreateAt()))
                .toList();
        List<AllocationResponse> allocations = allocationLedger.findByCourseIdOrderByIdDesc(id).stream()
                .map(a -> new AllocationResponse(a.getVisitDate(), a.getGrossCommissionAllocation(),
                        a.getTreatmentFeeAmount(), a.getOwnerNetCommission()))
                .toList();
        return new LedgerResponse(toResponse(course, ownerPatientId), movements, allocations);
    }

    @PostMapping("/api/course-usages")
    @Transactional
    public Response use(@RequestBody UseRequest request) {
        Course course = findCourse(request.coursePurchaseId());
        Patient owner = course.getPurchaserPatient();
        PatientVisit visit = request.appointmentId() == null ? walkInVisit(course, owner)
                : visits.findFirstByAppointmentIdOrderByIdDesc(request.appointmentId())
                        .orElseThrow(() -> new IllegalArgumentException("ยังไม่มีการเช็คอินสำหรับนัดหมายนี้"));
        // Always treats the Case Owner as the Treating PT - see class Javadoc.
        usageService.use(course, visit, owner, course.getCaseOwnerStaff(), LocalDate.now(), actor.currentId());
        return toResponse(courses.findById(course.getId()).orElseThrow(), owner.getId());
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
        Course course = findCourse(id);
        course.setCourseStatus(CourseStatus.Cancelled);
        course.setCourseUpdateAt(Instant.now());
        course.setCourseUpdateBy(actor.currentId());
        Course saved = courses.save(course);
        return toResponse(saved, saved.getPurchaserPatient().getId());
    }

    private PatientVisit walkInVisit(Course course, Patient owner) {
        PatientVisit visit = visitService.checkInWalkIn(owner, course.getReceipt().getBranch(),
                course.getCaseOwnerStaff(), actor.currentId());
        return visitService.complete(visit.getId(), actor.currentId());
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
                course.getCoursePrice(), course.getTotalVisits(), course.getBonusVisits() == null ? 0 : course.getBonusVisits(),
                course.getTotalVisits() + (course.getBonusVisits() == null ? 0 : course.getBonusVisits()),
                course.getVisitsUsed(), (int) remaining,
                course.getCourseCreateAt(), course.getExpiryDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                expired, usable, course.getCourseStatus().name(), course.getCommissionStatus().name(),
                course.getLockedCommissionRate(), course.getTotalCourseCommissionPool(),
                course.getCommissionAllocationPerVisit());
    }
}
