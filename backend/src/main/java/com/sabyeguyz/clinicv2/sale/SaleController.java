package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.auth.CurrentActor;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.catalog.ServiceRepository;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.patient.PatientRepository;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Matches frontend/src/api/client.ts's salesApi (/api/payments,
 * /api/receipts/{id}).
 *
 * <p>Known gap, flagged for the team: old "Sale" = one Receipt with many
 * line items and a shared pool of payments; v2's Payment_transaction FKs a
 * single Sales_transaction, not a Receipt. This controller only supports
 * (and enforces) exactly one item per create call - the "id" this API
 * returns/accepts is a SalesTransaction id, standing in for "the sale".
 * Multi-item receipts with one combined payment set are not representable
 * with the current v2 schema; the frontend already only ever sends one
 * item per call (services view), so this matches real usage today.</p>
 *
 * <p>Receipts are real now: Sales_transaction carries receipt_id, so the
 * receiptNo returned here is the Receipt's own number, and a bill discount
 * on the request is totalled onto the Receipt and allocated to the line.
 * Receipt numbering is still max-plus-one (see ReceiptService).</p>
 */
@RestController
public class SaleController {
    private final ReceiptService receiptService;
    private final SalesTransactionService salesTransactionService;
    private final PaymentTransactionService paymentTransactionService;
    private final SalesTransactionRepository transactions;
    private final PaymentTransactionRepository payments;
    private final PatientRepository patients;
    private final BranchRepository branches;
    private final StaffRepository staff;
    private final ServiceRepository services;
    private final PaymentMethodRepository paymentMethods;
    private final CurrentActor actor;

    public SaleController(ReceiptService receiptService, SalesTransactionService salesTransactionService,
                           PaymentTransactionService paymentTransactionService, SalesTransactionRepository transactions,
                           PaymentTransactionRepository payments, ReceiptRepository receipts,
                           PatientRepository patients, BranchRepository branches, StaffRepository staff,
                           ServiceRepository services, PaymentMethodRepository paymentMethods, CurrentActor actor) {
        this.receiptService = receiptService;
        this.salesTransactionService = salesTransactionService;
        this.paymentTransactionService = paymentTransactionService;
        this.transactions = transactions;
        this.payments = payments;
        this.patients = patients;
        this.branches = branches;
        this.staff = staff;
        this.services = services;
        this.paymentMethods = paymentMethods;
        this.actor = actor;
    }

    public record ItemRequest(String type, Long referenceId, int quantity, String discountType,
                               BigDecimal discountValue) {}
    public record PaymentRequest(String paymentMethodCode, BigDecimal amount) {}
    public record CreateRequest(Long patientId, String branchCode, Long salespersonStaffId, String note,
                                 List<ItemRequest> items, List<PaymentRequest> payments, String billDiscountType,
                                 BigDecimal billDiscountValue) {}
    public record ReverseRequest(String reason) {}

    public record PatientSummary(Long id, String hn, String fullName) {}
    public record SalespersonSummary(Long id, String employeeCode, String fullName) {}
    public record ItemResponse(Long id, String type, String description, int quantity, BigDecimal lineTotal) {}
    public record PaymentResponse(Long id, String type, String paymentMethodCode, BigDecimal amount) {}

    public record Summary(Long id, String receiptNo, PatientSummary patient, String branchCode,
                           SalespersonSummary salesperson, BigDecimal total, BigDecimal paid, String status,
                           Instant soldAt, Instant voidedAt, String voidReason, String note) {}
    public record Detail(Long id, String receiptNo, PatientSummary patient, String branchCode,
                          SalespersonSummary salesperson, BigDecimal total, BigDecimal paid, String status,
                          Instant soldAt, Instant voidedAt, String voidReason, String note,
                          List<ItemResponse> items, List<PaymentResponse> payments) {}
    public record PageResponse(List<Summary> items, int page, int size, long totalItems, int totalPages) {}

    @GetMapping("/api/payments")
    public PageResponse list(@RequestParam(required = false) Long patientId,
                              @RequestParam(required = false) String branchCode,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        Long branchId = branchCode == null || branchCode.isBlank() ? null : resolveBranch(branchCode).getId();
        Page<SalesTransaction> result = transactions.search(patientId, branchId, PageRequest.of(page, size));
        return new PageResponse(result.getContent().stream().map(this::toSummary).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/api/receipts/{id}")
    public Detail get(@PathVariable Long id) {
        return toDetail(findTransaction(id));
    }

    @PostMapping("/api/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public Summary create(@RequestBody CreateRequest request) {
        if (request.items() == null || request.items().size() != 1) {
            throw new IllegalArgumentException("รองรับการขาย 1 รายการต่อใบเสร็จเท่านั้นในตอนนี้");
        }
        Patient patient = patients.findById(request.patientId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบผู้ป่วย"));
        Branch branch = resolveBranch(request.branchCode());
        Staff salesperson = request.salespersonStaffId() == null ? null
                : staff.findById(request.salespersonStaffId()).orElseThrow(() -> new IllegalArgumentException("ไม่พบพนักงานขาย"));
        ItemRequest item = request.items().get(0);
        Service svc = services.findById(item.referenceId())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ/คอร์ส"));

        Discount discount = Discount.of(item.discountType(), item.discountValue());
        String receiptNo = receiptService.nextReceiptNo();
        Receipt receipt = receiptService.create(receiptNo, patient, branch, LocalDate.now(), actor.currentId());

        SalesTransaction transaction;
        if ("COURSE".equalsIgnoreCase(item.type())) {
            transaction = salesTransactionService.recordCoursePurchase(receipt, patient, branch, svc, salesperson,
                    null, null, null, null, discount, null, LocalDate.now(), actor.currentId()).transaction();
        } else {
            TransactionType type = "ASSESSMENT".equalsIgnoreCase(item.type())
                    ? TransactionType.Assessment : TransactionType.SingleVisit;
            transaction = salesTransactionService.recordVisitSale(receipt, patient, branch, svc, type, null,
                    salesperson, Math.max(1, item.quantity()), null, null, discount, actor.currentId());
        }
        receiptService.finalizeTotals(receipt, List.of(transaction),
                Discount.of(request.billDiscountType(), request.billDiscountValue()), actor.currentId());

        if (request.payments() != null) {
            for (PaymentRequest paymentRequest : request.payments()) {
                PaymentMethod method = paymentMethods.findByMethodNameIgnoreCase(paymentRequest.paymentMethodCode())
                        .orElseThrow(() -> new IllegalArgumentException("ไม่พบช่องทางชำระเงิน " + paymentRequest.paymentMethodCode()));
                paymentTransactionService.record(patient, branch, transaction, paymentRequest.amount(), method, actor.currentId());
            }
        }

        return toSummary(transaction);
    }

    @PostMapping("/api/payments/{id}/reverse")
    public Detail reverse(@PathVariable Long id, @RequestBody ReverseRequest request) {
        for (PaymentTransaction payment : payments.findByTransactionIdOrderByIdAsc(id)) {
            if (payment.isPaymentStatus()) {
                paymentTransactionService.voidPayment(payment.getId(), actor.currentId());
            }
        }
        salesTransactionService.voidTransaction(id, actor.currentId());
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
        Staff seller = t.getSalespersonStaff();
        return new Summary(t.getId(), t.getReceipt().getReceiptNo(),
                new PatientSummary(t.getPatient().getId(), t.getPatient().getPatientHn(),
                        t.getPatient().getPatientFname() + " " + t.getPatient().getPatientLname()),
                t.getBranch().getBranchCode(),
                seller == null ? null : new SalespersonSummary(seller.getId(), seller.getStaffCode(),
                        seller.getStaffFname() + " " + seller.getStaffLname()),
                t.getAmount().subtract(t.getAllocatedBillDiscountAmount()), payments.paidAmountOf(t.getId()),
                t.isTransactionStatus() ? "PAID" : "VOIDED",
                t.getTransactionDate(), null, null, null);
    }

    private Detail toDetail(SalesTransaction t) {
        Summary s = toSummary(t);
        List<ItemResponse> items = List.of(new ItemResponse(t.getId(), t.getTransactionType().name(),
                t.getService().getServiceName(), t.getQuantity(), t.getAmount()));
        List<PaymentResponse> paymentList = payments.findByTransactionIdOrderByIdAsc(t.getId()).stream()
                .map(p -> new PaymentResponse(p.getId(), p.isPaymentStatus() ? "PAYMENT" : "REFUND",
                        p.getPaymentMethod().getMethodName(), p.getAmount()))
                .toList();
        return new Detail(s.id(), s.receiptNo(), s.patient(), s.branchCode(), s.salesperson(), s.total(), s.paid(),
                s.status(), s.soldAt(), s.voidedAt(), s.voidReason(), s.note(), items, paymentList);
    }
}
