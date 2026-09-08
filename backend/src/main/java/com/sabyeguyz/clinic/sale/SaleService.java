package com.sabyeguyz.clinic.sale;

import com.sabyeguyz.clinic.branch.Branch;
import com.sabyeguyz.clinic.branch.BranchRepository;
import com.sabyeguyz.clinic.catalog.Course;
import com.sabyeguyz.clinic.catalog.CourseNotFoundException;
import com.sabyeguyz.clinic.catalog.CourseRepository;
import com.sabyeguyz.clinic.catalog.ServiceItem;
import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.course.CourseLedgerService;
import com.sabyeguyz.clinic.course.CoursePurchase;
import com.sabyeguyz.clinic.course.CoursePurchaseStatus;
import com.sabyeguyz.clinic.course.CourseReasonRequest;
import com.sabyeguyz.clinic.patient.Patient;
import com.sabyeguyz.clinic.patient.PatientNotFoundException;
import com.sabyeguyz.clinic.patient.PatientRepository;
import com.sabyeguyz.clinic.payment.PaymentMethod;
import com.sabyeguyz.clinic.payment.PaymentMethodRepository;
import com.sabyeguyz.clinic.staff.Staff;
import com.sabyeguyz.clinic.staff.StaffNotFoundException;
import com.sabyeguyz.clinic.staff.StaffRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ออกใบเสร็จและรับชำระเงิน ตามลำดับในผังงาน: เลือกวิธีคิดค่าบริการ → ชำระเงิน → สร้าง Course Balance
 *
 * <p>กติกาสองข้อที่ยึดไว้ตลอด</p>
 * <ul>
 *   <li>ราคามาจาก Service/Course master เท่านั้น ผู้เรียกส่งราคาเข้ามาไม่ได้</li>
 *   <li>ไม่มีการลบหรือแก้รายการเงินที่เกิดขึ้นแล้ว การยกเลิกคือการเขียนรายการคืนเงินเพิ่ม</li>
 * </ul>
 */
@Service
public class SaleService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Bangkok");
    private static final int MAX_PAGE_SIZE = 100;

    private final SaleRepository saleRepository;
    private final SaleItemRepository itemRepository;
    private final SalePaymentRepository paymentRepository;
    private final PatientRepository patientRepository;
    private final BranchRepository branchRepository;
    private final StaffRepository staffRepository;
    private final ServiceItemRepository serviceRepository;
    private final CourseRepository courseRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final CourseLedgerService courseLedgerService;
    private final ReceiptNumberGenerator receiptNumbers;

    public SaleService(SaleRepository saleRepository,
                       SaleItemRepository itemRepository,
                       SalePaymentRepository paymentRepository,
                       PatientRepository patientRepository,
                       BranchRepository branchRepository,
                       StaffRepository staffRepository,
                       ServiceItemRepository serviceRepository,
                       CourseRepository courseRepository,
                       PaymentMethodRepository paymentMethodRepository,
                       CourseLedgerService courseLedgerService,
                       ReceiptNumberGenerator receiptNumbers) {
        this.saleRepository = saleRepository;
        this.itemRepository = itemRepository;
        this.paymentRepository = paymentRepository;
        this.patientRepository = patientRepository;
        this.branchRepository = branchRepository;
        this.staffRepository = staffRepository;
        this.serviceRepository = serviceRepository;
        this.courseRepository = courseRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.courseLedgerService = courseLedgerService;
        this.receiptNumbers = receiptNumbers;
    }

    @Transactional
    public SaleResponse create(SaleRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new PatientNotFoundException(request.patientId()));
        if (!patient.isActive()) {
            throw new IllegalArgumentException("ผู้ป่วยถูกปิดการใช้งาน");
        }
        Branch branch = branchRepository.findByCodeAndActiveTrue(request.branchCode().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน"));
        Staff salesperson = resolveSalesperson(request.salespersonStaffId());

        // อ่านของที่ขายจาก master ก่อน เพื่อรู้ยอดรวมที่แท้จริงก่อนตัดสินใจว่าเงินที่รับมาพอหรือไม่
        List<PricedLine> lines = priceLines(request.items());
        BigDecimal total = lines.stream()
                .map(PricedLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ReceivedPayment> received = resolvePayments(request.payments());
        requirePaymentsToMatch(received, total);

        Instant soldAt = Instant.now();
        String receiptNo = receiptNumbers.next(branch.getId(), branch.getNumericCode(),
                LocalDate.ofInstant(soldAt, CLINIC_ZONE));
        Sale sale = saleRepository.save(
                new Sale(receiptNo, patient, branch, salesperson, total, trimmed(request.note()), soldAt));

        List<SaleItem> items = new ArrayList<>();
        for (PricedLine line : lines) {
            SaleItem item = line.toSaleItem(sale);
            // คอร์สที่ขายในใบนี้ถูกเครดิตสิทธิ์ทันที ผู้ป่วยจึงใช้สิทธิ์ในวันเดียวกันได้
            if (line.course() != null) {
                CoursePurchase purchase =
                        courseLedgerService.openPurchase(patient, line.course(), branch, soldAt);
                item.linkCoursePurchase(purchase);
            }
            items.add(itemRepository.save(item));
        }

        List<SalePayment> payments = received.stream()
                .map(entry -> paymentRepository.save(SalePayment.received(sale, entry.method(), entry.amount())))
                .toList();

        return SaleResponse.from(sale, paymentRepository.paidAmountOf(sale.getId()), items, payments);
    }

    /**
     * ยกเลิกใบเสร็จ: กลับรายการรับเงินทุกบรรทัด และยกเลิกคอร์สที่ใบนี้ขายไป
     * ไม่มีอะไรถูกลบ ครั้งที่ผู้ป่วยใช้สิทธิ์ไปแล้วยังคงอยู่ในบัญชีคอร์สตามเดิม
     */
    @Transactional
    public SaleResponse voidSale(long saleId, SaleVoidRequest request) {
        Sale sale = saleRepository.findByIdForUpdate(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));
        if (sale.getStatus() == SaleStatus.VOIDED) {
            throw new SaleException("ใบเสร็จนี้ถูกยกเลิกไปแล้ว");
        }
        String reason = request.reason().trim();

        for (SalePayment payment : paymentRepository.findBySaleIdOrderByIdAsc(saleId)) {
            if (payment.getType() == SalePaymentType.PAYMENT
                    && !paymentRepository.existsByReversesPaymentId(payment.getId())) {
                paymentRepository.save(SalePayment.refundOf(payment, reason));
            }
        }

        String courseReason = "ยกเลิกใบเสร็จ " + sale.getReceiptNo() + ": " + reason;
        for (SaleItem item : itemRepository.findBySaleIdOrderByIdAsc(saleId)) {
            CoursePurchase purchase = item.getCoursePurchase();
            // ข้ามคอร์สที่ถูกยกเลิกไปแล้วด้วยเหตุผลอื่น ไม่ยกเลิกซ้ำ
            if (purchase != null && purchase.getStatus() == CoursePurchaseStatus.ACTIVE) {
                courseLedgerService.cancel(purchase.getId(), new CourseReasonRequest(courseReason));
            }
        }

        sale.voidSale(reason, Instant.now());
        return describe(sale);
    }

    @Transactional(readOnly = true)
    public SaleResponse get(long saleId) {
        return describe(saleRepository.findById(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId)));
    }

    /**
     * ลิสต์ใบเสร็จแบบแบ่งหน้า ไม่ระบุตัวกรองก็ได้ทั้งหมด เรียงใหม่ไปเก่า
     * คืนแบบย่อ ไม่มีรายการสินค้าและรายการชำระเงิน ให้ไปดึงรายละเอียดจาก {@link #get(long)}
     */
    @Transactional(readOnly = true)
    public SaleSearchResponse search(Long patientId, String branchCode, int page, int size) {
        validatePagination(page, size);
        if (patientId != null && !patientRepository.existsById(patientId)) {
            throw new PatientNotFoundException(patientId);
        }
        Long branchId = branchCode == null || branchCode.isBlank() ? null
                : branchRepository.findByCodeAndActiveTrue(branchCode.trim().toUpperCase())
                        .orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขาที่เปิดใช้งาน " + branchCode))
                        .getId();

        Page<Sale> sales = saleRepository.search(patientId, branchId, PageRequest.of(page, size,
                Sort.by("soldAt").descending().and(Sort.by("id").descending())));
        if (sales.isEmpty()) {
            return new SaleSearchResponse(List.of(), sales.getNumber(), sales.getSize(),
                    sales.getTotalElements(), sales.getTotalPages());
        }

        // ดึงยอดรับจริงของทั้งหน้าในคราวเดียว ไม่ยิง query ต่อหนึ่งใบ
        Map<Long, BigDecimal> paid = paymentRepository
                .paidAmountsOf(sales.getContent().stream().map(Sale::getId).toList()).stream()
                .collect(Collectors.toMap(SalePaidAmount::saleId, SalePaidAmount::amount));

        return new SaleSearchResponse(
                sales.getContent().stream()
                        .map(sale -> SaleSummaryResponse.from(sale,
                                paid.getOrDefault(sale.getId(), BigDecimal.ZERO)))
                        .toList(),
                sales.getNumber(), sales.getSize(), sales.getTotalElements(), sales.getTotalPages());
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page ต้องไม่น้อยกว่า 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size ต้องอยู่ระหว่าง 1 ถึง " + MAX_PAGE_SIZE);
        }
    }

    private SaleResponse describe(Sale sale) {
        return SaleResponse.from(sale,
                paymentRepository.paidAmountOf(sale.getId()),
                itemRepository.findBySaleIdOrderByIdAsc(sale.getId()),
                paymentRepository.findBySaleIdOrderByIdAsc(sale.getId()));
    }

    private Staff resolveSalesperson(Long staffId) {
        if (staffId == null) {
            return null;
        }
        Staff salesperson = staffRepository.findById(staffId).orElseThrow(StaffNotFoundException::new);
        if (!salesperson.isActive()) {
            throw new IllegalArgumentException("พนักงานขายคนนี้ถูกปิดการใช้งาน");
        }
        return salesperson;
    }

    private List<PricedLine> priceLines(List<SaleRequest.Item> requested) {
        List<PricedLine> lines = new ArrayList<>();
        for (SaleRequest.Item item : requested) {
            if (item.type() == SaleItemType.COURSE) {
                if (item.quantity() != 1) {
                    throw new IllegalArgumentException(
                            "คอร์สต้องขายทีละ 1 ใบ เพราะแต่ละใบมียอดคงเหลือและวันหมดอายุของตัวเอง");
                }
                Course course = courseRepository.findById(item.referenceId())
                        .orElseThrow(CourseNotFoundException::new);
                if (!course.isActive()) {
                    throw new IllegalArgumentException("คอร์ส " + course.getCode() + " ถูกปิดการใช้งาน");
                }
                lines.add(new PricedLine(item.type(), null, course, course.getPrice(), 1));
            } else {
                ServiceItem service = serviceRepository.findById(item.referenceId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "ไม่พบบริการ id " + item.referenceId()));
                if (!service.isActive()) {
                    throw new IllegalArgumentException("บริการ " + service.getCode() + " ถูกปิดการใช้งาน");
                }
                if (service.getPrice() == null || service.getPrice().signum() <= 0) {
                    throw new IllegalArgumentException(
                            "บริการ " + service.getCode() + " ยังไม่ได้ตั้งราคา ขายไม่ได้");
                }
                lines.add(new PricedLine(item.type(), service, null, service.getPrice(), item.quantity()));
            }
        }
        return lines;
    }

    private List<ReceivedPayment> resolvePayments(List<SaleRequest.Payment> requested) {
        return requested.stream().map(entry -> {
            PaymentMethod method = paymentMethodRepository.findByCode(entry.paymentMethodCode().trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "ไม่พบช่องทางชำระเงิน " + entry.paymentMethodCode()));
            if (!method.isActive()) {
                throw new IllegalArgumentException("ช่องทางชำระเงิน " + method.getCode() + " ถูกปิดการใช้งาน");
            }
            return new ReceivedPayment(method, entry.amount());
        }).toList();
    }

    private void requirePaymentsToMatch(List<ReceivedPayment> received, BigDecimal total) {
        BigDecimal paid = received.stream()
                .map(ReceivedPayment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // compareTo ไม่ใช่ equals เพราะ 1500 กับ 1500.00 คือยอดเดียวกันแต่ scale ต่างกัน
        if (paid.compareTo(total) != 0) {
            throw new IllegalArgumentException(
                    "ยอดชำระ %s ไม่ตรงกับยอดที่ต้องชำระ %s".formatted(paid.toPlainString(), total.toPlainString()));
        }
    }

    private static String trimmed(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** บรรทัดที่ตีราคาจาก master แล้ว แต่ยังไม่ผูกกับใบเสร็จ */
    private record PricedLine(SaleItemType type, ServiceItem service, Course course,
                              BigDecimal unitPrice, int quantity) {
        BigDecimal lineTotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }

        SaleItem toSaleItem(Sale sale) {
            return course != null
                    ? SaleItem.ofCourse(sale, course)
                    : SaleItem.ofService(sale, type, service, quantity);
        }
    }

    private record ReceivedPayment(PaymentMethod method, BigDecimal amount) {}
}
