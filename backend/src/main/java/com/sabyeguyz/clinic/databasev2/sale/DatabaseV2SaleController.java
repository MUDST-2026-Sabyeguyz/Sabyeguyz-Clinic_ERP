package com.sabyeguyz.clinic.databasev2.sale;

import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2BranchRepository;
import com.sabyeguyz.clinic.databasev2.catalog.DatabaseV2ServiceRepository;
import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2ConflictException;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2NotFoundException;
import com.sabyeguyz.clinic.databasev2.patient.DatabaseV2PatientRepository;
import com.sabyeguyz.clinic.databasev2.payment.DatabaseV2PaymentMethodRepository;
import com.sabyeguyz.clinic.databasev2.staff.DatabaseV2StaffRepository;
import com.sabyeguyz.clinic.sale.SaleItemType;
import com.sabyeguyz.clinic.sale.SalePaymentType;
import com.sabyeguyz.clinic.sale.SaleRequest;
import com.sabyeguyz.clinic.sale.SaleResponse;
import com.sabyeguyz.clinic.sale.SaleSearchResponse;
import com.sabyeguyz.clinic.sale.SaleStatus;
import com.sabyeguyz.clinic.sale.SaleSummaryResponse;
import com.sabyeguyz.clinic.sale.SaleVoidRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("database-v2")
@RequestMapping("/api")
public class DatabaseV2SaleController {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Bangkok");
    private static final ZoneOffset BANGKOK_OFFSET = ZoneOffset.ofHours(7);

    private final DatabaseV2SaleRepository saleRepository;
    private final DatabaseV2PatientRepository patientRepository;
    private final DatabaseV2BranchRepository branchRepository;
    private final DatabaseV2StaffRepository staffRepository;
    private final DatabaseV2ServiceRepository serviceRepository;
    private final DatabaseV2PaymentMethodRepository paymentMethodRepository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2SaleController(
            DatabaseV2SaleRepository saleRepository,
            DatabaseV2PatientRepository patientRepository,
            DatabaseV2BranchRepository branchRepository,
            DatabaseV2StaffRepository staffRepository,
            DatabaseV2ServiceRepository serviceRepository,
            DatabaseV2PaymentMethodRepository paymentMethodRepository,
            CurrentActorProvider actorProvider) {
        this.saleRepository = saleRepository;
        this.patientRepository = patientRepository;
        this.branchRepository = branchRepository;
        this.staffRepository = staffRepository;
        this.serviceRepository = serviceRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.actorProvider = actorProvider;
    }

    private record PricedItem(
            SaleItemType type,
            DatabaseV2ServiceRepository.ServiceModel service,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal
    ) {}

    private record ResolvedPayment(
            DatabaseV2PaymentMethodRepository.PaymentMethodRecord method,
            BigDecimal amount
    ) {}

    @PostMapping("/payments")
    @Transactional
    public ResponseEntity<SaleResponse> create(@Valid @RequestBody SaleRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);

        patientRepository.findById(request.patientId())
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบผู้ป่วย id " + request.patientId()));

        DatabaseV2Branch branch = branchRepository.findByCode(request.branchCode().trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา " + request.branchCode()));

        Long salespersonId = null;
        if (request.salespersonStaffId() != null) {
            DatabaseV2StaffRepository.StaffModel staff = staffRepository.findById(request.salespersonStaffId())
                    .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบพนักงานขาย id " + request.salespersonStaffId()));
            if (!staff.active()) {
                throw new IllegalArgumentException("พนักงานขายคนนี้ถูกปิดการใช้งาน");
            }
            salespersonId = staff.id();
        }

        // Validate items and prices from master service table
        List<PricedItem> pricedItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (SaleRequest.Item item : request.items()) {
            if (item.type() == SaleItemType.COURSE && item.quantity() != 1) {
                throw new IllegalArgumentException("คอร์สต้องขายทีละ 1 ใบ");
            }

            DatabaseV2ServiceRepository.ServiceModel service = serviceRepository.findById(item.referenceId())
                    .orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ/คอร์ส id " + item.referenceId()));

            if (!service.active()) {
                throw new IllegalArgumentException("รายการ " + service.code() + " ถูกปิดการใช้งาน");
            }

            BigDecimal unitPrice = service.price();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()));
            total = total.add(lineTotal);

            pricedItems.add(new PricedItem(item.type(), service, item.quantity(), unitPrice, lineTotal));
        }

        // Validate payments
        List<ResolvedPayment> resolvedPayments = new ArrayList<>();
        BigDecimal paymentTotal = BigDecimal.ZERO;

        for (SaleRequest.Payment p : request.payments()) {
            DatabaseV2PaymentMethodRepository.PaymentMethodRecord pm = paymentMethodRepository.findByName(p.paymentMethodCode())
                    .orElseThrow(() -> new IllegalArgumentException("ไม่พบวิธีการชำระเงิน " + p.paymentMethodCode()));

            if (!pm.active()) {
                throw new IllegalArgumentException("วิธีการชำระเงิน " + pm.name() + " ถูกปิดการใช้งาน");
            }

            paymentTotal = paymentTotal.add(p.amount());
            resolvedPayments.add(new ResolvedPayment(pm, p.amount()));
        }

        if (paymentTotal.compareTo(total) != 0) {
            throw new IllegalArgumentException("ยอดเงินที่ชำระ (" + paymentTotal + ") ไม่ตรงกับยอดที่ต้องชำระ (" + total + ")");
        }

        LocalDate saleDate = LocalDate.now(CLINIC_ZONE);
        LocalDateTime now = LocalDateTime.now();

        // 1. Allocate receipt
        DatabaseV2SaleRepository.GeneratedReceipt allocated = saleRepository.allocateReceipt();
        saleRepository.insertReceipt(allocated.id(), allocated.receiptNo(), request.patientId(), branch.getId(), saleDate, actorId);

        // 2. Create transactions and courses
        Long primaryTxId = null;
        for (PricedItem pi : pricedItems) {
            Long txId = saleRepository.createSalesTransaction(
                    request.patientId(), branch.getId(), pi.service().id(),
                    pi.type().name(), null, salespersonId,
                    pi.quantity(), pi.unitPrice(), pi.lineTotal(),
                    now, null, actorId
            );

            if (primaryTxId == null) {
                primaryTxId = txId;
            }

            if (pi.type() == SaleItemType.COURSE) {
                int totalVisits = pi.service().totalVisits() != null ? pi.service().totalVisits() : 10;
                int bonusVisits = pi.service().bonusVisits() != null ? pi.service().bonusVisits() : 0;
                int validityDays = pi.service().validityDays() != null ? pi.service().validityDays() : 365;

                Long sellerStaffId = salespersonId;
                if (sellerStaffId == null) {
                    sellerStaffId = staffRepository.findFirstActive(branch.getId())
                            .map(DatabaseV2StaffRepository.StaffModel::id)
                            .orElse(null);
                }

                Long courseId = saleRepository.createCourse(
                        allocated.receiptNo(), pi.service().name(), pi.unitPrice(),
                        totalVisits, bonusVisits, validityDays,
                        pi.service().id(), allocated.id(), saleDate,
                        sellerStaffId, sellerStaffId, actorId
                );

                saleRepository.updateTransactionCourseId(txId, courseId);
            }
        }

        // 3. Create payments (attached to the primary transaction)
        for (ResolvedPayment rp : resolvedPayments) {
            saleRepository.createPaymentTransaction(
                    request.patientId(), branch.getId(), primaryTxId,
                    rp.amount(), rp.method().id(), now, actorId
            );
        }

        SaleResponse response = toResponse(saleRepository.findById(allocated.id()).orElseThrow());
        return ResponseEntity.created(URI.create("/api/receipts/" + response.id())).body(response);
    }

    @GetMapping("/payments")
    public SaleSearchResponse list(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String branchCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException("page ต้องไม่น้อยกว่า 0");
        }
        int limit = Math.max(1, Math.min(size, 100));
        int offset = page * limit;

        List<DatabaseV2SaleRepository.SaleSummaryRecord> records = saleRepository.search(patientId, branchCode, limit, offset);
        int total = saleRepository.countSearch(patientId, branchCode);
        int totalPages = (int) Math.ceil((double) total / limit);

        List<SaleSummaryResponse> items = records.stream().map(DatabaseV2SaleController::toSummaryResponse).toList();
        return new SaleSearchResponse(items, page, limit, total, totalPages);
    }

    @GetMapping("/receipts/{id}")
    public SaleResponse get(@PathVariable Long id) {
        return toResponse(saleRepository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบใบเสร็จ id " + id)));
    }

    @PostMapping("/payments/{id}/reverse")
    @Transactional
    public SaleResponse reverse(@PathVariable Long id, @Valid @RequestBody SaleVoidRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);

        saleRepository.findById(id)
                .orElseThrow(() -> new DatabaseV2NotFoundException("ไม่พบใบเสร็จ id " + id));

        if (saleRepository.isVoided(id)) {
            throw new DatabaseV2ConflictException("ใบเสร็จนี้ถูกยกเลิกไปแล้ว");
        }

        saleRepository.voidReceipt(id, request.reason().trim(), actorId);

        return toResponse(saleRepository.findById(id).orElseThrow());
    }

    public static SaleResponse toResponse(DatabaseV2SaleRepository.SaleRecord r) {
        SaleResponse.PatientSummary patient = new SaleResponse.PatientSummary(r.patientId(), r.patientHn(), r.patientFullName());
        SaleResponse.SalespersonSummary salesperson = r.salespersonId() != null
                ? new SaleResponse.SalespersonSummary(r.salespersonId(), r.salespersonCode(), r.salespersonFullName())
                : null;

        SaleStatus status = "VOIDED".equals(r.status()) ? SaleStatus.VOIDED : SaleStatus.PAID;

        List<SaleResponse.SaleItemResponse> items = r.items().stream().map(item -> new SaleResponse.SaleItemResponse(
                item.id(),
                SaleItemType.valueOf(item.type()),
                item.description(),
                item.unitPrice(),
                item.quantity(),
                item.lineTotal(),
                item.coursePurchaseId()
        )).toList();

        List<SaleResponse.SalePaymentResponse> payments = r.payments().stream().map(p -> new SaleResponse.SalePaymentResponse(
                p.id(),
                SalePaymentType.valueOf(p.type()),
                p.paymentMethodCode(),
                p.amount(),
                p.reason(),
                p.reversesPaymentId(),
                p.createdAt().toInstant(BANGKOK_OFFSET)
        )).toList();

        return new SaleResponse(
                r.id(),
                r.receiptNo(),
                patient,
                r.branchCode(),
                salesperson,
                r.total(),
                r.paid(),
                status,
                r.note(),
                r.soldAt().toInstant(BANGKOK_OFFSET),
                r.voidedAt() != null ? r.voidedAt().toInstant(BANGKOK_OFFSET) : null,
                r.voidReason(),
                items,
                payments
        );
    }

    public static SaleSummaryResponse toSummaryResponse(DatabaseV2SaleRepository.SaleSummaryRecord r) {
        SaleResponse.PatientSummary patient = new SaleResponse.PatientSummary(r.patientId(), r.patientHn(), r.patientFullName());
        SaleResponse.SalespersonSummary salesperson = r.salespersonId() != null
                ? new SaleResponse.SalespersonSummary(r.salespersonId(), r.salespersonCode(), r.salespersonFullName())
                : null;

        SaleStatus status = "VOIDED".equals(r.status()) ? SaleStatus.VOIDED : SaleStatus.PAID;

        return new SaleSummaryResponse(
                r.id(),
                r.receiptNo(),
                patient,
                r.branchCode(),
                salesperson,
                r.total(),
                r.paid(),
                status,
                r.soldAt().toInstant(BANGKOK_OFFSET),
                r.voidedAt() != null ? r.voidedAt().toInstant(BANGKOK_OFFSET) : null
        );
    }
}
