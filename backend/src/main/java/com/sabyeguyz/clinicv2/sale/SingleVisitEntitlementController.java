package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.patient.PatientRepository;
import com.sabyeguyz.clinicv2.role.PermissionService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Patient single-visit service balances and appointment redemption endpoints. */
@RestController
@Transactional(readOnly = true)
public class SingleVisitEntitlementController {
    private final SingleVisitEntitlementRepository entitlements;
    private final SingleVisitEntitlementUsageRepository usages;
    private final SingleVisitEntitlementService service;
    private final PatientRepository patients;
    private final BranchAccessPolicy branchAccess;
    private final PermissionService permissions;
    private final CurrentActor actor;

    public SingleVisitEntitlementController(SingleVisitEntitlementRepository entitlements,
                                            SingleVisitEntitlementUsageRepository usages,
                                            SingleVisitEntitlementService service, PatientRepository patients,
                                            BranchAccessPolicy branchAccess, PermissionService permissions,
                                            CurrentActor actor) {
        this.entitlements = entitlements;
        this.usages = usages;
        this.service = service;
        this.patients = patients;
        this.branchAccess = branchAccess;
        this.permissions = permissions;
        this.actor = actor;
    }

    public record EntitlementResponse(Long id, Long saleTransactionId, Long patientId, String branchCode,
                                      Long serviceId, String serviceName, int purchased, int used, int remaining,
                                      boolean serviceActive, Instant purchasedAt, String receiptNo) {}
    public record UseRequest(Long appointmentId, Long treatingStaffId) {}
    public record UseResponse(Long id, Long entitlementId, Long appointmentId, Long visitId, Instant usedAt) {}
    public record ReverseRequest(String reason) {}

    @GetMapping("/api/patients/{patientId}/single-visit-entitlements")
    public List<EntitlementResponse> patientEntitlements(@PathVariable Long patientId) {
        Patient patient = patients.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบผู้ป่วย"));
        branchAccess.requireAccess(patient.getBranch());
        var branchScope = branchAccess.scope(null);
        return service.listForPatient(patientId).stream()
                .filter(balance -> branchScope == null
                        || branchScope.getId().equals(balance.entitlement().getTransaction().getBranch().getId()))
                .map(this::toResponse).toList();
    }

    @PostMapping("/api/single-visit-entitlements/{entitlementId}/uses")
    @Transactional
    public UseResponse use(@PathVariable Long entitlementId, @RequestBody UseRequest request) {
        if (request.appointmentId() == null || request.treatingStaffId() == null) {
            throw new IllegalArgumentException("ต้องระบุนัดหมายและนักกายภาพผู้รักษา");
        }
        SingleVisitEntitlement entitlement = entitlements.findById(entitlementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบสิทธิ์บริการรายครั้ง"));
        branchAccess.requireAccess(entitlement.getTransaction().getBranch());
        Long actorId = actor.requireId();
        SingleVisitEntitlementUsage usage = service.redeem(entitlementId, request.appointmentId(),
                request.treatingStaffId(), entitlement.getTransaction().getPatient(), actorId);
        return new UseResponse(usage.getId(), entitlementId, usage.getAppointment().getId(),
                usage.getVisit().getId(), usage.getUsedAt());
    }

    @PostMapping("/api/single-visit-entitlement-uses/{usageId}/reverse")
    @Transactional
    public UseResponse reverse(@PathVariable Long usageId, @RequestBody ReverseRequest request) {
        SingleVisitEntitlementUsage usage = usages.findById(usageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ไม่พบประวัติใช้สิทธิ์บริการ"));
        branchAccess.requireAccess(usage.getEntitlement().getTransaction().getBranch());
        permissions.requireAllowed("COURSE_REVERSE", actor.requireId());
        SingleVisitEntitlementUsage reversed = service.reverse(usageId, request.reason(), actor.requireId());
        return new UseResponse(reversed.getId(), reversed.getEntitlement().getId(), reversed.getAppointment().getId(),
                reversed.getVisit().getId(), reversed.getUsedAt());
    }

    private EntitlementResponse toResponse(SingleVisitEntitlementService.Balance balance) {
        SingleVisitEntitlement entitlement = balance.entitlement();
        SalesTransaction sale = entitlement.getTransaction();
        return new EntitlementResponse(entitlement.getId(), sale.getId(), sale.getPatient().getId(),
                sale.getBranch().getBranchCode(), sale.getService().getId(), sale.getItemNameSnapshot(),
                entitlement.getPurchasedQuantity(), balance.used(), balance.remaining(), sale.getService().isServiceStatus(),
                sale.getTransactionDate(), sale.getReceipt().getReceiptNo());
    }
}
