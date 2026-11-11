package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.enums.AuditAction;
import com.sabyeguyz.clinicv2.enums.ReasonCode;
import com.sabyeguyz.clinicv2.role.PermissionService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Refunds of a payment (id taken from {@code payments[].id} of GET /api/receipts/{id}): a refund is
 * requested (Pending), then completed once the money is handed back, or failed. The total of
 * Pending and Completed refunds may never exceed the payment. Money handling is manager-only.
 */
@Transactional
@RestController
@PreAuthorize("hasRole('MANAGER')")
public class RefundController {
    private final BranchAccessPolicy branchAccess;
    private final RefundService refundService;
    private final RefundTransactionRepository refunds;
    private final PaymentTransactionRepository payments;
    private final PaymentMethodRepository paymentMethods;
    private final PermissionService permissions;
    private final CurrentActor actor;
    private final com.sabyeguyz.clinicv2.audit.AuditLogService audit;

    public RefundController(RefundService refundService, RefundTransactionRepository refunds,
                             PaymentTransactionRepository payments, PaymentMethodRepository paymentMethods,
                             PermissionService permissions, CurrentActor actor,
                                     com.sabyeguyz.clinicv2.audit.AuditLogService audit,
                                     BranchAccessPolicy branchAccess) {
        this.refundService = refundService;
        this.refunds = refunds;
        this.payments = payments;
        this.paymentMethods = paymentMethods;
        this.permissions = permissions;
        this.actor = actor;
        this.audit = audit;
        this.branchAccess = branchAccess;
    }

    /** items: per course of a course receipt, the amount refunded and the unused sessions to cancel. */
    public record RefundRequest(BigDecimal amount, String paymentMethodCode, String reason,
                                 List<RefundService.ItemRequest> items, ReasonCode reasonCode) {}

    public record ItemResponse(
            Long courseId,
            BigDecimal refundAmount,
            Integer cancelledVisits,
            Long courseRefundItemId,
            Long rightsCancellationItemId) {}

    public record Response(Long id, Long paymentId, BigDecimal amount, String paymentMethodCode, String status,
                            Instant requestedAt, Instant refundedAt, List<ItemResponse> items) {}

    @PostMapping("/api/payments/{paymentId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public Response request(@PathVariable Long paymentId, @RequestBody RefundRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()
                || request.reason().trim().length() > 255) {
            throw new IllegalArgumentException("ต้องระบุเหตุผลความยาวไม่เกิน 255 ตัวอักษร");
        }
        PaymentTransaction payment = accessiblePayment(paymentId);
        PaymentMethod method = request.paymentMethodCode() == null ? null
                : paymentMethods.findByMethodNameIgnoreCase(request.paymentMethodCode())
                        .orElseThrow(() -> new IllegalArgumentException("ไม่พบช่องทางชำระเงิน " + request.paymentMethodCode()));
        var permission = permissions.requireAllowed("SALE_CANCEL", actor.currentId());
        RefundTransaction refund = refundService.request(payment, request.amount(), method, permission,
                request.reasonCode() == null ? ReasonCode.Other : request.reasonCode(),
                request.reason().trim(), actor.currentId(), request.items());
        ReasonCode reasonCode = request.reasonCode() == null ? ReasonCode.Other : request.reasonCode();
        audit.recordChange("refund_transaction", String.valueOf(refund.getId()), AuditAction.INSERT, null,
                com.sabyeguyz.clinicv2.audit.AuditLogService.fields(
                        "status", "Pending", "amount", request.amount(), "payment_id", paymentId,
                        "reason_code", reasonCode.name(), "reason", request.reason().trim()), actor.currentId());
        return toResponse(refund);
    }

    @GetMapping("/api/payments/{paymentId}/refunds")
    public List<Response> forPayment(@PathVariable Long paymentId) {
        accessiblePayment(paymentId);
        return refunds.findByOriginalPaymentId(paymentId).stream().map(this::toResponse).toList();
    }

    @PostMapping("/api/refunds/{id}/complete")
    public Response complete(@PathVariable Long id) {
        permissions.requireAllowed("SALE_CANCEL", actor.currentId());
        requireRefundAccess(id);
        RefundTransaction refund = refundService.complete(id, actor.currentId());
        audit.record("refund_transaction", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE, "Pending", "Completed", actor.currentId());
        return toResponse(refund);
    }

    @PostMapping("/api/refunds/{id}/void")
    public Response voidRequest(@PathVariable Long id) {
        permissions.requireAllowed("SALE_CANCEL", actor.currentId());
        requireRefundAccess(id);
        String previousStatus = refundService.lockStatusForUpdate(id).name();
        RefundTransaction refund = refundService.voidRequest(id, actor.currentId());
        audit.record("refund_transaction", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE, previousStatus, "Voided", actor.currentId());
        return toResponse(refund);
    }

    @PostMapping("/api/refunds/{id}/fail")
    public Response fail(@PathVariable Long id) {
        permissions.requireAllowed("SALE_CANCEL", actor.currentId());
        requireRefundAccess(id);
        RefundTransaction refund = refundService.fail(id);
        audit.record("refund_transaction", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE, "Pending", "Failed", actor.currentId());
        return toResponse(refund);
    }

    @PostMapping("/api/refunds/{id}/retry")
    public Response retry(@PathVariable Long id) {
        permissions.requireAllowed("SALE_CANCEL", actor.currentId());
        requireRefundAccess(id);
        String previousStatus = refundService.lockStatusForUpdate(id).name();
        RefundTransaction refund = refundService.retry(id);
        audit.record("refund_transaction", String.valueOf(id),
                com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                previousStatus, "Pending", actor.currentId());
        return toResponse(refund);
    }

    /** The original receipt determines access for every refund route, including reads. */
    private PaymentTransaction accessiblePayment(Long paymentId) {
        PaymentTransaction payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการชำระเงิน"));
        branchAccess.requireAccess(payment.getReceipt().getBranch());
        return payment;
    }

    private void requireRefundAccess(Long refundId) {
        RefundTransaction refund = refunds.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการคืนเงิน"));
        branchAccess.requireAccess(refund.getOriginalPayment().getReceipt().getBranch());
    }

    private Response toResponse(RefundTransaction refund) {
        List<ItemResponse> items = refundService.itemsOf(refund.getId()).stream()
                .map(item -> new ItemResponse(
                        item.getCourse().getId(),
                        item.getRefundAmount(),
                        item.getCancelledVisits(),
                        item.getId(),
                        item.getRightsCancellationItem() == null
                                ? null
                                : item.getRightsCancellationItem().getId()))
                .toList();
        return new Response(refund.getId(), refund.getOriginalPayment().getId(), refund.getRefundAmount(),
                refund.getPaymentMethod().getMethodName(), refund.getRefundStatus().name(), refund.getRequestedAt(),
                refund.getRefundedAt(), items);
    }
}
