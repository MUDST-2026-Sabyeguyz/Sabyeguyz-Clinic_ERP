package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.auth.CurrentActor;
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
                                     com.sabyeguyz.clinicv2.audit.AuditLogService audit) {
        this.refundService = refundService;
        this.refunds = refunds;
        this.payments = payments;
        this.paymentMethods = paymentMethods;
        this.permissions = permissions;
        this.actor = actor;
        this.audit = audit;
    }

    public record RefundRequest(BigDecimal amount, String paymentMethodCode, String reason) {}

    public record Response(Long id, Long paymentId, BigDecimal amount, String paymentMethodCode, String status,
                            Instant requestedAt, Instant refundedAt) {}

    @PostMapping("/api/payments/{paymentId}/refunds")
    @ResponseStatus(HttpStatus.CREATED)
    public Response request(@PathVariable Long paymentId, @RequestBody RefundRequest request) {
        PaymentTransaction payment = payments.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบรายการชำระเงิน"));
        PaymentMethod method = request.paymentMethodCode() == null ? null
                : paymentMethods.findByMethodNameIgnoreCase(request.paymentMethodCode())
                        .orElseThrow(() -> new IllegalArgumentException("ไม่พบช่องทางชำระเงิน " + request.paymentMethodCode()));
        var permission = permissions.getOrCreate("SALE_CANCEL", "Sale", actor.currentId());
        RefundTransaction refund = refundService.request(payment, request.amount(), method, permission,
                request.reason(), actor.currentId());
        audit.record("refund_transaction", String.valueOf(refund.getId()), com.sabyeguyz.clinicv2.enums.AuditAction.INSERT, null,
                "Pending " + request.amount() + " (payment " + paymentId + ")", actor.currentId());
        return toResponse(refund);
    }

    @GetMapping("/api/payments/{paymentId}/refunds")
    public List<Response> forPayment(@PathVariable Long paymentId) {
        return refunds.findByOriginalPaymentId(paymentId).stream().map(RefundController::toResponse).toList();
    }

    @PostMapping("/api/refunds/{id}/complete")
    public Response complete(@PathVariable Long id) {
        RefundTransaction refund = refundService.complete(id, actor.currentId());
        audit.record("refund_transaction", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE, "Pending", "Completed", actor.currentId());
        return toResponse(refund);
    }

    @PostMapping("/api/refunds/{id}/fail")
    public Response fail(@PathVariable Long id) {
        RefundTransaction refund = refundService.fail(id);
        audit.record("refund_transaction", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE, "Pending", "Failed", actor.currentId());
        return toResponse(refund);
    }

    private static Response toResponse(RefundTransaction refund) {
        return new Response(refund.getId(), refund.getOriginalPayment().getId(), refund.getRefundAmount(),
                refund.getPaymentMethod().getMethodName(), refund.getRefundStatus().name(), refund.getRequestedAt(),
                refund.getRefundedAt());
    }
}
