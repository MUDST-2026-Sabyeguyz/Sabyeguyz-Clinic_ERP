package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.config.ClinicTime;
import org.springframework.transaction.annotation.Transactional;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.catalog.ServiceRepository;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.patient.PatientRepository;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.role.PermissionService;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffRepository;
import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's salesApi (/api/payments,
 * /api/receipts/{id}).
 *
 * <p>The schema now supports a receipt with several lines and payments on the
 * receipt, and this API accepts multiple items per create call, and the "id" it returns/accepts is that
 * line's SalesTransaction id standing in for "the sale". Payments listed for
 * a sale are the whole receipt's payments.</p>
 *
 * <p>Receipts are real now: Sales_transaction carries receipt_id, so the
 * receiptNo returned here is the Receipt's own number, and a bill discount
 * on the request is totalled onto the Receipt and allocated to the line.
 * Receipt numbering uses the atomic branch/month counter (see ReceiptService).</p>
 */
@Transactional
@RestController
public class SaleController {
    private final ReceiptService receiptService;
    private final SalesTransactionService salesTransactionService;
    private final PaymentTransactionService paymentTransactionService;
    private final TransactionCancellationService cancellations;
    private final TransactionCancellationRepository cancellationRecords;
    private final PermissionService permissions;
    private final SalesTransactionRepository transactions;
    private final PaymentTransactionRepository payments;
    private final PatientRepository patients;
    private final BranchRepository branches;
    private final StaffRepository staff;
    private final ServiceRepository services;
    private final PaymentMethodRepository paymentMethods;
    private final CurrentActor actor;
    private final SingleVisitFeeAccrual singleVisitFees;
    private final com.sabyeguyz.clinicv2.audit.AuditLogService audit;
    private final com.sabyeguyz.clinicv2.appointment.PatientVisitService visitService;
    private final com.sabyeguyz.clinicv2.auth.AppUserRepository users;

    private final com.sabyeguyz.clinicv2.auth.BranchAccessPolicy branchAccess;

    public SaleController(ReceiptService receiptService, SalesTransactionService salesTransactionService,
                           PaymentTransactionService paymentTransactionService,
                           TransactionCancellationService cancellations, TransactionCancellationRepository cancellationRecords, PermissionService permissions,
                           SalesTransactionRepository transactions,
                           PaymentTransactionRepository payments, ReceiptRepository receipts,
                           PatientRepository patients, BranchRepository branches, StaffRepository staff,
                           ServiceRepository services, PaymentMethodRepository paymentMethods, CurrentActor actor,
                                     com.sabyeguyz.clinicv2.audit.AuditLogService audit,
                                     com.sabyeguyz.clinicv2.appointment.PatientVisitService visitService,
                                     com.sabyeguyz.clinicv2.auth.AppUserRepository users,
                                     SingleVisitFeeAccrual singleVisitFees,
                                     com.sabyeguyz.clinicv2.auth.BranchAccessPolicy branchAccess) {
        this.branchAccess = branchAccess;
        this.singleVisitFees = singleVisitFees;
        this.visitService = visitService;
        this.users = users;
        this.receiptService = receiptService;
        this.salesTransactionService = salesTransactionService;
        this.paymentTransactionService = paymentTransactionService;
        this.cancellations = cancellations;
        this.cancellationRecords = cancellationRecords;
        this.permissions = permissions;
        this.transactions = transactions;
        this.payments = payments;
        this.patients = patients;
        this.branches = branches;
        this.staff = staff;
        this.services = services;
        this.paymentMethods = paymentMethods;
        this.actor = actor;
        this.audit = audit;
    }

    public record ItemRequest(String type, Long referenceId, int quantity, String discountType,
                               BigDecimal discountValue) {}
    public record PaymentRequest(String paymentMethodCode, BigDecimal amount) {}
    /** treatingStaffId (optional) is the PT who treated the single-visit lines; default: the salesperson, else the signed-in staff. */
    public record CreateRequest(Long patientId, String branchCode, Long salespersonStaffId, String note, List<ItemRequest> items,
                                List<PaymentRequest> payments, String billDiscountType, BigDecimal billDiscountValue,
                                Long treatingStaffId, String paymentLabelCode, Long appointmentId) {}
    public record ReverseRequest(String reason) {}

    private Staff singleVisitTreatingStaff(CreateRequest request, Staff salesperson) {
        if (request.treatingStaffId() != null) {
            return staff.findById(request.treatingStaffId())
                    .orElseThrow(() -> new IllegalArgumentException("ไม่พบนักกายภาพผู้รักษา"));
        }
        if (salesperson != null) return salesperson;
        Staff own = users.findById(actor.requireId()).map(user -> user.getStaff()).orElse(null);
        if (own == null) {
            throw new IllegalArgumentException("ต้องระบุนักกายภาพผู้รักษา (treatingStaffId) สำหรับการรักษารายครั้ง");
        }
        return own;
    }

    public record PatientSummary(Long id, String hn, String fullName) {}
    public record SalespersonSummary(Long id, String employeeCode, String fullName) {}
    public record ItemResponse(Long id, String type, String description, int quantity, BigDecimal lineTotal) {}
    public record PaymentResponse(Long id, String type, String paymentMethodCode, BigDecimal amount) {}

    public record Summary(Long id, String receiptNo, PatientSummary patient, String branchCode,
                           SalespersonSummary salesperson, BigDecimal total, BigDecimal paid, String status,
                           Instant soldAt, Instant voidedAt, String voidReason, String note, String paymentLabel,
                           Long receiptId, BigDecimal subtotalBeforeDiscount, BigDecimal billDiscountAmount,
                           BigDecimal taxableAmount, BigDecimal taxAmount, BigDecimal netAmount,
                           String itemType, String itemDescription) {}
    public record Detail(Long id, String receiptNo, PatientSummary patient, String branchCode,
                          SalespersonSummary salesperson, BigDecimal total, BigDecimal paid, String status,
                          Instant soldAt, Instant voidedAt, String voidReason, String note,
                          List<ItemResponse> items, List<PaymentResponse> payments, String paymentLabel,
                          Long receiptId, BigDecimal subtotalBeforeDiscount, BigDecimal billDiscountAmount,
                           BigDecimal taxableAmount, BigDecimal taxAmount, BigDecimal netAmount) {}
    public record PageResponse(List<Summary> items, int page, int size, long totalItems, int totalPages) {}

    @GetMapping("/api/payments")
    public PageResponse list(@RequestParam(required = false) Long patientId,
                              @RequestParam(required = false) String branchCode,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(defaultValue = "false") boolean receiptsOnly) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be nonnegative and size must be between 1 and 100");
        }
        Branch scopedBranch = branchAccess.scope(branchCode);
        Long branchId = scopedBranch == null ? null : scopedBranch.getId();
        PageRequest paging = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate", "id"));
        Page<SalesTransaction> result = receiptsOnly
                ? transactions.searchReceipts(patientId, branchId, paging)
                : transactions.search(patientId, branchId, paging);
        return new PageResponse(result.getContent().stream().map(t -> receiptsOnly
                ? toSummary(t, t.getReceipt().getNetAmount()) : toSummary(t)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/api/receipts/{id}")
    public Detail get(@PathVariable Long id) {
        return toDetail(findTransaction(id));
    }

    @PostMapping("/api/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public Summary create(@RequestBody CreateRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("ต้องมีรายการขายอย่างน้อย 1 รายการ");
        }
        if (request.payments() != null && request.payments().size() > 1) {
            throw new IllegalArgumentException(
                    "หนึ่งใบเสร็จมีรายการชำระเงินได้ไม่เกินหนึ่งรายการ");
        }

        PaymentRequest paymentRequest = null;
        if (request.payments() != null && !request.payments().isEmpty()) {
            paymentRequest = request.payments().get(0);
            if (paymentRequest == null) {
                throw new IllegalArgumentException("ต้องระบุข้อมูลการชำระเงิน");
            }
            PaymentMethod.canonicalName(paymentRequest.paymentMethodCode());
        }
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"));
        Branch branch = resolveBranch(request.branchCode());
        Staff salesperson = request.salespersonStaffId() == null ? null
                : staff.findById(request.salespersonStaffId()).orElseThrow(() -> new IllegalArgumentException("ไม่พบพนักงานขาย"));

        String receiptNo = receiptService.nextReceiptNo(branch, ClinicTime.today());
        Receipt receipt = receiptService.create(
                receiptNo, patient, branch, ClinicTime.today(),
                request.paymentLabelCode(), actor.currentId());

        List<SalesTransaction> lines = new java.util.ArrayList<>();
        PatientVisit appointmentVisit = null;
        for (ItemRequest item : request.items()) {
            Service svc = services.findById(item.referenceId())
                    .orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ/คอร์ส"));
            Discount discount = Discount.of(item.discountType(), item.discountValue());
            if ("COURSE".equalsIgnoreCase(item.type())) {
                lines.add(salesTransactionService.recordCoursePurchase(receipt, patient, branch, svc, salesperson,
                        null, null, null, discount, ClinicTime.today(), actor.currentId()).transaction());
            } else {
                TransactionType type = "ASSESSMENT".equalsIgnoreCase(item.type())
                        ? TransactionType.Assessment : TransactionType.SingleVisit;
                // A single visit is treated by one PT, and the sale is tied to that visit.
                Staff treating = singleVisitTreatingStaff(request, salesperson);
                if (request.appointmentId() == null) {
                    throw new IllegalArgumentException(
                            "ต้องสร้างนัด NEW หรือ CURRENT ก่อนรับบริการ " + "และส่ง appointmentId มาด้วย");
                }

                branchAccess.requireAccess(branch);
                if (appointmentVisit == null) {
                    appointmentVisit = visitService.createForService(request.appointmentId(), patient, branch, treating, actor.currentId());
                }
                PatientVisit visit = appointmentVisit;
                lines.add(salesTransactionService.recordVisitSale(receipt, patient, branch, svc, type, treating,
                        salesperson, Math.max(1, item.quantity()), null, visit, discount, actor.currentId()));
            }
        }
        receiptService.finalizeTotals(receipt, lines,
                Discount.of(request.billDiscountType(), request.billDiscountValue()), actor.currentId());

        if (paymentRequest != null) {
            String methodName = PaymentMethod.canonicalName(paymentRequest.paymentMethodCode());
            PaymentMethod method = paymentMethods
                    .findByMethodNameIgnoreCase(methodName)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "ไม่พบช่องทางชำระเงิน " + methodName));

            paymentTransactionService.record(
                    patient, branch, receipt, paymentRequest.amount(), method, actor.currentId());

            if (appointmentVisit != null) {
                appointmentVisit = visitService.completeForService(
                        appointmentVisit.getId(),
                        request.appointmentId(),
                        actor.currentId());
            }

            singleVisitFees.accrue(receipt, lines, actor.currentId());
        } else if (appointmentVisit != null) {
            throw new IllegalArgumentException(
                    "ต้องรับชำระเงินสำเร็จก่อนยืนยันการรับบริการจากนัด");
        }

        audit.record("receipt", String.valueOf(receipt.getId()), com.sabyeguyz.clinicv2.enums.AuditAction.INSERT, null,
                receiptNo + " (" + lines.size() + " รายการ)", actor.currentId());
        return toSummary(lines.get(0), lines);
    }

    @PostMapping("/api/payments/{id}/reverse")
    public Detail reverse(@PathVariable Long id, @RequestBody ReverseRequest request) {
        SalesTransaction transaction = findTransaction(id);
        branchAccess.requireAccess(transaction.getBranch(), "ยกเลิกได้เฉพาะรายการของสาขาตัวเอง รายการต่างสาขาดูรายละเอียดได้อย่างเดียว");
        Permission permission = permissions.requireAllowed("SALE_CANCEL", actor.currentId());
        cancellations.voidSale(transaction, permission, request.reason(), actor.currentId());
        audit.record("sales_transaction", String.valueOf(id), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE, "PAID", "VOIDED: " + request.reason(),
                actor.currentId());
        return toDetail(findTransaction(id));
    }

    private SalesTransaction findTransaction(Long id) {
        return transactions.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบใบเสร็จ"));
    }

    private Branch resolveBranch(String branchCode) {
        return branches.findByBranchCode(branchCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + branchCode));
    }

    private Summary toSummary(SalesTransaction t) {
        return toSummary(t, List.of(t));
    }

    /** Summary of a receipt: the first line identifies it, the total spans every line in {@code lines}. */
    private Summary toSummary(SalesTransaction t, List<SalesTransaction> lines) {
        BigDecimal total = lines.stream().map(l -> l.getAmount().subtract(l.getAllocatedBillDiscountAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return toSummary(t, total);
    }

    private Summary toSummary(SalesTransaction t, BigDecimal total) {
        Receipt receipt = t.getReceipt();
        Staff seller = t.getSalespersonStaff();
        TransactionCancellation cancellation = t.isTransactionStatus() ? null
                : cancellationRecords.findFirstByTransactionReceiptIdOrderByCancelledAtDesc(t.getReceipt().getId()).orElse(null);
        return new Summary(t.getId(), t.getReceipt().getReceiptNo(),
                new PatientSummary(t.getPatient().getId(), t.getPatient().getPatientHn(),
                        t.getPatient().getPatientFname() + " " + t.getPatient().getPatientLname()),
                t.getBranch().getBranchCode(),
                seller == null ? null : new SalespersonSummary(seller.getId(), seller.getStaffCode(),
                        seller.getStaffFname() + " " + seller.getStaffLname()),
                total, payments.paidAmountOf(t.getReceipt().getId()),
                t.isTransactionStatus() ? "PAID" : "VOIDED",
                t.getTransactionDate(), cancellation == null ? null : cancellation.getCancelledAt(),
                cancellation == null ? null : cancellation.getReason(), null, receipt.getPaymentLabel().name(), receipt.getId(), receipt.getSubtotalBeforeDiscount(),
                receipt.getBillDiscountAmount(), receipt.getTaxableAmount(), receipt.getTaxAmount(), receipt.getNetAmount(),
                t.getTransactionType().name(), t.getItemNameSnapshot());
    }

    private Detail toDetail(SalesTransaction t) {
        List<SalesTransaction> lines = transactions.findByReceiptId(t.getReceipt().getId());
        Summary s = toSummary(t, t.getReceipt().getNetAmount());
        List<ItemResponse> items = lines.stream().map(l -> new ItemResponse(l.getId(), l.getTransactionType().name(),
                l.getItemNameSnapshot(), l.getQuantity(), l.getAmount())).toList();
        List<PaymentResponse> paymentList = payments.findByReceiptIdOrderByIdAsc(t.getReceipt().getId()).stream()
                .map(p -> new PaymentResponse(p.getId(), p.isPaymentStatus() ? "PAYMENT" : "REFUND",
                        p.getPaymentMethod().getMethodName(), p.getAmount()))
                .toList();
        return new Detail(s.id(), s.receiptNo(), s.patient(), s.branchCode(), s.salesperson(), s.total(), s.paid(),
                s.status(), s.soldAt(), s.voidedAt(), s.voidReason(), s.note(), items, paymentList, s.paymentLabel(),
                s.receiptId(), s.subtotalBeforeDiscount(), s.billDiscountAmount(), s.taxableAmount(), s.taxAmount(), s.netAmount());
    }
}
