package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.audit.AuditLogService;
import com.sabyeguyz.clinicv2.auth.AppUserRepository;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import com.sabyeguyz.clinicv2.enums.PaymentStatus;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Reopening a month, the monthly payment of each staff member (prepare, close, reopen, pay) and pay adjustments. Only the
 * Manager changes anything here; a PT reads their own payments with {@code GET /api/commission/my-payments}. Every change of
 * state is written to the audit log.
 */
@Transactional
@RestController
@RequestMapping("/api/commission")
@PreAuthorize("hasRole('MANAGER')")
public class CommissionPayrollController {
    private final MonthlyCommissionClosingService closingService;
    private final MonthlyCommissionClosingRepository closings;
    private final StaffMonthlyPaymentService paymentService;
    private final StaffPayAdjustmentService adjustmentService;
    private final CurrentActor actor;
    private final BranchAccessPolicy branchAccess;
    private final AppUserRepository users;
    private final AuditLogService audit;

    public CommissionPayrollController(MonthlyCommissionClosingService closingService, StaffMonthlyPaymentService paymentService,
                                       StaffPayAdjustmentService adjustmentService, CurrentActor actor, AppUserRepository users,
                                       AuditLogService audit, BranchAccessPolicy branchAccess,
                                       MonthlyCommissionClosingRepository closings) {
        this.closings = closings;
        this.branchAccess = branchAccess;
        this.closingService = closingService;
        this.paymentService = paymentService;
        this.adjustmentService = adjustmentService;
        this.actor = actor;
        this.users = users;
        this.audit = audit;
    }

    public record ReasonRequest(String reason) {}
    public record ReferenceRequest(String reference) {}
    /** paidDate (yyyy-mm-dd) defaults to today; reference is the transfer or slip number. */
    public record PayRequest(java.time.LocalDate paidDate, String reference) {}
    public record ApplicationRequest(String month, BigDecimal amount, String branchCode) {}

    public record PaymentResponse(Long id, Long staffId, String staffName, String month, String status, BigDecimal courseCommission,
                                  BigDecimal singleVisitFee, BigDecimal substituteFee, BigDecimal adjustment, BigDecimal net,
                                  Instant paidAt, Long closingId, long pendingSessions, List<ApplicationResponse> applications,
                                  java.time.LocalDate paidDate, String paymentReference,
                                  String branchCode, String branchName) {}
    public record ApplicationResponse(Long id, Long adjustmentId, BigDecimal amount, String status, boolean approved,
                                       String approvalReference) {}
    public record AdjustmentResponse(Long id, Long staffId, String staffName, String originalMonth, String appliedMonth,
                                      BigDecimal amount, String reason, String status, BigDecimal processed, BigDecimal remaining,
                                      Long allocationId, Long singleVisitFeeId, Long courseId, Long visitId, Long saleTransactionId,
                                      List<ApplicationResponse> applications) {}
    public record ClosingStateResponse(Long id, String month, Long staffId, String status) {}

    // ---------- reopening a closed month ----------

    @PostMapping("/closings/{closingId}/reopen")
    public ClosingStateResponse reopenClosing(@PathVariable Long closingId, @RequestBody ReasonRequest request) {
        MonthlyCommissionClosing existing = closings.findById(closingId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการปิดยอด"));

        if (existing.getBranch() == null) {
            throw new IllegalStateException(
                    "งวดปิดยอดนี้ยังไม่มีสาขา ต้องตรวจข้อมูลก่อนทำรายการ");
        }

        branchAccess.requireAccess(existing.getBranch());
        MonthlyCommissionClosing closing = closingService.reopen(closingId, request.reason(), actor.currentId());
        audit.recordChange("monthly_commission_closing", String.valueOf(closingId), AuditAction.UPDATE,
                com.sabyeguyz.clinicv2.audit.AuditLogService.fields("closing_status", "Closed"),
                com.sabyeguyz.clinicv2.audit.AuditLogService.fields("closing_status", "Open",
                        "reopen_reason", request.reason().trim(), "closing_month", closing.getClosingMonth(),
                        "staff_id", closing.getStaff().getId(),
                        "branch_id", closing.getBranch().getId(),
                        "monthly_course_sales", closing.getMonthlyCourseSales(),
                        "commission_scheme_version", closing.getCommissionSchemeVersion(),
                        "calculated_commission_rate", closing.getCalculatedCommissionRate(),
                        "locked_commission_rate", closing.getLockedCommissionRate()), actor.currentId());
        return new ClosingStateResponse(closing.getId(), closing.getClosingMonth(), closing.getStaff().getId(),
                closing.getClosingStatus().name().toUpperCase(java.util.Locale.ROOT));
    }

    // ---------- monthly payments ----------

    @GetMapping("/payments")
    public List<PaymentResponse> payments(
            @RequestParam String month,
            @RequestParam String branchCode) {

        StaffMonthlyPaymentService.requireMonth(month);

        var branch = branchAccess.scope(branchCode);
        if (branch == null) {
            throw new IllegalArgumentException("ต้องเลือกสาขาที่ต้องการดูงวดจ่าย");
        }

        return paymentService.ofMonth(month, branch.getId())
                .stream()
                .map(this::toPayment)
                .toList();
    }

    @GetMapping("/payments/{id}")
    public PaymentResponse payment(@PathVariable Long id) {
        return toPayment(requirePaymentAccess(id));
    }

    /** Creates or recalculates Open payments for the selected branch and month. */
    @PostMapping("/payments/prepare")
    public List<PaymentResponse> prepare(
            @RequestParam String month,
            @RequestParam String branchCode) {

        StaffMonthlyPaymentService.requireMonth(month);

        var branch = branchAccess.scope(branchCode);
        if (branch == null) {
            throw new IllegalArgumentException("ต้องเลือกสาขาของงวดจ่าย");
        }

        List<PaymentResponse> result =
                paymentService.prepare(month, branch, actor.currentId())
                        .stream()
                        .map(this::toPayment)
                        .toList();

        audit.record(
                "staff_monthly_payment",
                branch.getBranchCode() + "/" + month,
                AuditAction.UPDATE,
                null,
                "prepared " + result.size() + " payments",
                actor.currentId());

        return result;
    }

    /** Closes monthly payments together for the selected branch. */
    @PostMapping("/payments/close")
    public List<PaymentResponse> closeBranchMonth(
            @RequestParam String month,
            @RequestParam String branchCode) {

        StaffMonthlyPaymentService.requireMonth(month);

        var branch = branchAccess.scope(branchCode);
        if (branch == null) {
            throw new IllegalArgumentException("ต้องเลือกสาขาที่ต้องการปิดงวด");
        }

        List<PaymentResponse> result =
                paymentService.closeBranchMonth(month, branch, actor.currentId())
                        .stream()
                        .map(this::toPayment)
                        .toList();

        audit.record(
                "staff_monthly_payment",
                branch.getBranchCode() + "/" + month,
                AuditAction.UPDATE,
                null,
                "branch monthly payments closed",
                actor.currentId());

        return result;
    }

    @PostMapping("/payments/{id}/reopen")
    public PaymentResponse reopenPayment(@PathVariable Long id) {
        requirePaymentAccess(id);
        StaffMonthlyPayment payment = paymentService.reopen(id, actor.currentId());
        audit.record("staff_monthly_payment", String.valueOf(id), AuditAction.UPDATE, "Closed", "Open", actor.currentId());
        return toPayment(payment);
    }

    @PostMapping("/payments/{id}/pay")
    public PaymentResponse pay(@PathVariable Long id, @RequestBody(required = false) PayRequest request) {
        requirePaymentAccess(id);
        StaffMonthlyPayment payment = request == null ? paymentService.pay(id, actor.currentId())
                : paymentService.pay(id, request.paidDate(), request.reference(), actor.currentId());
        audit.record("staff_monthly_payment", String.valueOf(id), AuditAction.UPDATE, "Closed",
                "Paid net " + payment.getNetPaymentAmount(), actor.currentId());
        return toPayment(payment);
    }

    /** A PT reads their own payments (all statuses, newest first); the Manager may pass staffId. */
    @GetMapping("/my-payments")
    @PreAuthorize("isAuthenticated()")
    public List<PaymentResponse> myPayments(@RequestParam(required = false) String month) {
        Staff me = users.findById(actor.requireId()).map(user -> user.getStaff()).orElse(null);
        if (me == null) {
            return List.of();
        }
        return paymentService.ofStaff(me.getId()).stream()
                .filter(payment -> month == null || payment.getPaymentMonth().equals(month))
                .map(this::toPayment).toList();
    }

    // ---------- adjustments ----------

    @GetMapping("/adjustments")
    public List<AdjustmentResponse> adjustments(@RequestParam(required = false) Long staffId) {
        return adjustmentService.list(staffId).stream().map(this::toAdjustment).toList();
    }

    @GetMapping("/adjustments/{id}")
    public AdjustmentResponse adjustment(@PathVariable Long id) {
        return toAdjustment(adjustmentService.find(id));
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentResponse createAdjustment(@RequestBody StaffPayAdjustmentService.CreateRequest request) {
        StaffPayAdjustment adjustment = adjustmentService.create(request, actor.currentId());
        audit.record("staff_pay_adjustment", String.valueOf(adjustment.getId()), AuditAction.INSERT, null,
                adjustment.getAmount() + " for staff " + adjustment.getStaff().getId() + ": " + adjustment.getReason(), actor.currentId());
        return toAdjustment(adjustment);
    }

    @PostMapping("/adjustments/{id}/approve")
    public AdjustmentResponse approveAdjustment(@PathVariable Long id) {
        StaffPayAdjustment adjustment = adjustmentService.approve(id, actor.currentId());
        audit.record("staff_pay_adjustment", String.valueOf(id), AuditAction.UPDATE, "Pending", "Approved", actor.currentId());
        return toAdjustment(adjustment);
    }

    @PostMapping("/adjustments/{id}/cancel")
    public AdjustmentResponse cancelAdjustment(@PathVariable Long id) {
        StaffPayAdjustment adjustment = adjustmentService.cancel(id);
        audit.record("staff_pay_adjustment", String.valueOf(id), AuditAction.UPDATE, null, "Cancelled", actor.currentId());
        return toAdjustment(adjustment);
    }

    @PostMapping("/adjustments/{id}/external-review")
    public AdjustmentResponse externalReview(@PathVariable Long id) {
        StaffPayAdjustment adjustment = adjustmentService.externalReview(id);
        audit.record("staff_pay_adjustment", String.valueOf(id), AuditAction.UPDATE, null, "ExternalReview", actor.currentId());
        return toAdjustment(adjustment);
    }

    @PostMapping("/adjustments/{id}/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse createApplication(
            @PathVariable Long id,
            @RequestBody ApplicationRequest request) {

        var branch = branchAccess.scope(request.branchCode());
        if (branch == null) {
            throw new IllegalArgumentException(
                    "ต้องเลือกสาขาของงวดจ่ายที่จะนำ Adjustment ไปใช้");
        }

        AdjustmentApplication application =
                adjustmentService.createApplication(
                        id, request.month(), request.amount(),
                        branch, actor.currentId());

        audit.record(
                "adjustment_application",
                String.valueOf(application.getId()),
                AuditAction.INSERT,
                null,
                "Draft " + application.getAmount()
                        + " in " + request.month()
                        + " branch " + branch.getBranchCode(),
                actor.currentId());

        return toApplication(application);
    }

    @PostMapping("/adjustment-applications/{id}/approve")
    public ApplicationResponse approveApplication(@PathVariable Long id, @RequestBody(required = false) ReferenceRequest request) {
        AdjustmentApplication application = adjustmentService.approveApplication(id,
                request == null ? null : request.reference(), actor.currentId());
        audit.record("adjustment_application", String.valueOf(id), AuditAction.UPDATE, "Draft", "Draft approved", actor.currentId());
        return toApplication(application);
    }

    @PostMapping("/adjustment-applications/{id}/cancel")
    public ApplicationResponse cancelApplication(@PathVariable Long id) {
        AdjustmentApplication application = adjustmentService.cancelApplication(id);
        audit.record("adjustment_application", String.valueOf(id), AuditAction.UPDATE, "Draft", "Cancelled", actor.currentId());
        return toApplication(application);
    }

    // ---------- responses ----------
    /** Checks access using the branch recorded on the payment. */
    private StaffMonthlyPayment requirePaymentAccess(Long id) {
        StaffMonthlyPayment payment = paymentService.find(id);

        if (payment.getBranch() == null) {
            throw new IllegalStateException(
                    "งวดจ่ายนี้ยังไม่มีสาขา ต้องตรวจข้อมูลก่อนทำรายการ");
        }

        branchAccess.requireAccess(payment.getBranch());
        return payment;
    }

    private PaymentResponse toPayment(StaffMonthlyPayment payment) {
        if (payment.getBranch() == null) {
            throw new IllegalStateException(
                    "งวดจ่ายนี้ยังไม่มีสาขา ต้องตรวจข้อมูลก่อนแสดงผล");
        }

        long pending = payment.getPaymentStatus() == PaymentStatus.Paid ? 0
                : paymentService.totals(
                payment.getStaff().getId(),
                payment.getPaymentMonth(),
                payment.getBranch().getId()).pendingSessions();

        Staff person = payment.getStaff();
        return new PaymentResponse(payment.getId(), person.getId(), person.getStaffFname() + " " + person.getStaffLname(),
                payment.getPaymentMonth(), payment.getPaymentStatus().name().toUpperCase(java.util.Locale.ROOT),
                payment.getCourseCommissionAmount(), payment.getSingleVisitFeeAmount(), payment.getSubstituteFeeAmount(),
                payment.getAdjustmentAmount(), payment.getNetPaymentAmount(), payment.getPaidAt(),
                payment.getClosing() == null ? null : payment.getClosing().getId(), pending,
                paymentService.applicationsOf(payment.getId()).stream().map(CommissionPayrollController::toApplication).toList(),
                payment.getPaidDate(), payment.getPaymentReference(), payment.getBranch().getBranchCode(), payment.getBranch().getBranchName());
    }

    private AdjustmentResponse toAdjustment(StaffPayAdjustment adjustment) {
        Staff person = adjustment.getStaff();
        return new AdjustmentResponse(adjustment.getId(), person.getId(), person.getStaffFname() + " " + person.getStaffLname(),
                adjustment.getOriginalMonth(), adjustment.getAppliedMonth(), adjustment.getAmount(), adjustment.getReason(),
                adjustment.getAdjustmentStatus().name(), adjustment.getProcessedAmount(), adjustment.getRemainingAmount(),
                adjustment.getAllocation() == null ? null : adjustment.getAllocation().getId(),
                adjustment.getSingleVisitFee() == null ? null : adjustment.getSingleVisitFee().getId(),
                adjustment.getCourse() == null ? null : adjustment.getCourse().getId(),
                adjustment.getVisit() == null ? null : adjustment.getVisit().getId(),
                adjustment.getSaleTransaction() == null ? null : adjustment.getSaleTransaction().getId(),
                adjustmentService.applicationsOf(adjustment.getId()).stream().map(CommissionPayrollController::toApplication).toList());
    }

    private static ApplicationResponse toApplication(AdjustmentApplication application) {
        return new ApplicationResponse(application.getId(), application.getAdjustment().getId(), application.getAmount(),
                application.getApplicationStatus().name(), application.getApprovedBy() != null, application.getApprovalReference());
    }
}
