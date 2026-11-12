package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.enums.ReceiptStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receipts (Clinic ERP Workflow Brief Section 5).
 *
 * <p>Receipt numbers use REYYMMNNNN, allocated atomically per branch/month from receipt_sequence.</p>
 *
 * <p>R32 confirms zero tax: taxable_amount is the amount after the bill discount, tax_amount is
 * zero and net_amount equals taxable_amount.</p>
 */
@Service
public class ReceiptService {
    private static final String RECEIPT_NO_FORMAT = "^RE[0-9]{8}$";

    private final ReceiptRepository repository;

    private final CourseRepository courses;
    private final PaymentLabelOptionRepository paymentLabels;

    public ReceiptService(ReceiptRepository repository, CourseRepository courses, PaymentLabelOptionRepository paymentLabels) {
        this.repository = repository;
        this.courses = courses;
        this.paymentLabels = paymentLabels;
    }

<<<<<<< HEAD
=======
    private List<BigDecimal> allocateBillDiscount(
            List<BigDecimal> amounts,
            BigDecimal total,
            BigDecimal discount
    ) {
        BigDecimal zero = new BigDecimal("0.00");

        if (discount.signum() < 0 || discount.compareTo(total) > 0) {
            throw new IllegalArgumentException(
                    "ส่วนลดท้ายบิลต้องอยู่ระหว่าง 0 ถึงยอดก่อนลดท้ายบิล"
            );
        }

        List<BigDecimal> shares = new ArrayList<>();
        BigDecimal remainingDiscount = discount;
        BigDecimal remainingAmount = total;

        for (int i = 0; i < amounts.size(); i++) {
            BigDecimal amount = amounts.get(i);

            if (amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("ยอดรายการต้องไม่เป็น null หรือติดลบ");
            }

            remainingAmount = remainingAmount.subtract(amount);
            BigDecimal share;

            if (i == amounts.size() - 1) {
                share = remainingDiscount;
            } else if (total.signum() == 0) {
                share = zero;
            } else {
                share = discount.multiply(amount).divide(total, 2, RoundingMode.HALF_UP);
                BigDecimal minimumShare = remainingDiscount.subtract(remainingAmount).max(zero);
                BigDecimal maximumShare = amount.min(remainingDiscount);
                share = share.max(minimumShare).min(maximumShare);
            }

            if (share.signum() < 0 || share.compareTo(amount) > 0) {
                throw new IllegalArgumentException("ส่วนลดจัดสรรต้องอยู่ระหว่าง 0 ถึงยอดรายการ");
            }

            shares.add(share);
            remainingDiscount = remainingDiscount.subtract(share);
        }

        if (remainingDiscount.signum() != 0) {
            throw new IllegalStateException("ยอดส่วนลดจัดสรรรวมไม่ตรงกับส่วนลดท้ายบิล");
        }

        return shares;
    }

>>>>>>> 3c7da8a915aea6793ee49626494196b33cc60e17
    /**
     * Assigns an enabled payment label selected for a new receipt.
     * Writes both the new FK and the legacy enum during migration.
     */
    public void assignPaymentLabel(Receipt receipt, String code) {
        if (receipt == null) {
            throw new IllegalArgumentException("ต้องระบุใบเสร็จ");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("ต้องเลือกสถานะการชำระเงิน");
        }

        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        String databaseCode = "PAID".equals(normalized)
                ? "FULL" : normalized;

        PaymentLabelOption option = paymentLabels.findByCode(databaseCode)
                .orElseThrow(() ->
                        new IllegalArgumentException("ไม่พบสถานะการชำระเงิน"));

        if (!option.isEnabled()) {
            throw new IllegalArgumentException(
                    "สถานะการชำระเงินนี้ถูกปิดใช้งาน");
        }

        com.sabyeguyz.clinicv2.enums.PaymentLabel legacyLabel =
                switch (option.getCode()) {
                    case "FULL" ->
                            com.sabyeguyz.clinicv2.enums.PaymentLabel.FullyPaid;
                    case "INSTALLMENT" ->
                            com.sabyeguyz.clinicv2.enums.PaymentLabel.Installment;
                    case "DEPOSIT" ->
                            com.sabyeguyz.clinicv2.enums.PaymentLabel.Deposit;
                    default ->
                            throw new IllegalArgumentException(
                                    "รหัสสถานะการชำระเงินไม่รองรับ");
                };

        receipt.setPaymentLabelOption(option);
        receipt.setPaymentLabel(legacyLabel);
    }

    @Transactional(readOnly = true)
    public String nextReceiptNo() {
        return "RE%08d".formatted(repository.findMaxReceiptSequence() + 1);
    }

    /** Atomic allocation per branch/month, using the latest receipt_sequence table. */
    @Transactional
    public String nextReceiptNo(Branch branch, LocalDate date) {
        String month = date.format(java.time.format.DateTimeFormatter.ofPattern("yyMM"));
        int running = repository.nextRunningNumber(branch.getId(), month);
        return "RE%s%04d".formatted(month, running);
    }

    @Transactional
    public Receipt create(String receiptNo, Patient patient, Branch branch, LocalDate saleDate, String paymentLabelCode, Long actorId) {
        String no = requireText(receiptNo, "receiptNo");
        if (!no.matches(RECEIPT_NO_FORMAT)) {
            throw new IllegalArgumentException("เลขที่ใบเสร็จต้องอยู่ในรูปแบบ REYYMMNNNN");
        }
        if (patient == null) {
            throw new IllegalArgumentException("ต้องระบุผู้ป่วย");
        }
        if (branch == null) {
            throw new IllegalArgumentException("ต้องระบุสาขา");
        }
        if (repository.findByReceiptNoAndBranchId(no, branch.getId()).isPresent()) {
            throw new IllegalArgumentException("เลขที่ใบเสร็จซ้ำในสาขา");
        }
        Receipt receipt = new Receipt();
        receipt.setReceiptNo(no);
        receipt.setPatient(patient);
        receipt.setBranch(branch);
        receipt.setSaleDate(saleDate == null ? ClinicTime.now() : saleDate.atStartOfDay());
        receipt.setReceiptCreateAt(Instant.now());
        receipt.setReceiptCreateBy(actorId);
        assignPaymentLabel(receipt, paymentLabelCode);
        return repository.save(receipt);
    }

    /**
     * Calculates receipt totals from effective sales and existing-course payments.
     * Subtotal includes sales quantities multiplied by unit prices before discounts.
     * Net amount includes sales amounts after line discounts, plus existing-course
     * payments, minus the bill discount. Bill discounts are allocated across both
     * item types without changing sales commissionable amounts.
     */
    @Transactional
    public Receipt finalizeTotals(
            Receipt receipt, List<SalesTransaction> lines, Discount billDiscount, Long actorId
    ) {
        return finalizeTotals(receipt, lines, List.of(), billDiscount, actorId);
    }

    @Transactional
    public Receipt finalizeTotals(
            Receipt receipt, List<SalesTransaction> lines,
            List<ReceiptCoursePayment> coursePayments,
            Discount billDiscount, Long actorId
    ) {
        if (receipt == null) {
            throw new IllegalArgumentException("ต้องระบุใบเสร็จ");
        }

        lines = lines == null
                ? List.of()
                : lines.stream().filter(SalesTransaction::isTransactionStatus).toList();

        coursePayments = coursePayments == null
                ? List.of()
                : coursePayments;

        if (lines.isEmpty() && coursePayments.isEmpty()) {
            throw new IllegalArgumentException("ใบเสร็จต้องมีรายการอย่างน้อย 1 รายการ");
        }

        BigDecimal coursePaymentTotal = coursePayments.stream()
                .map(ReceiptCoursePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal subtotal = lines.stream()
                .map(line -> line.getUnitPrice()
                        .multiply(BigDecimal.valueOf(line.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(coursePaymentTotal)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal amountBeforeBillDiscount = lines.stream()
                .map(SalesTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(coursePaymentTotal)
                .setScale(2, RoundingMode.HALF_UP);

        Discount bill = billDiscount == null
                ? Discount.none()
                : billDiscount;

        boolean hasLineDiscount = lines.stream()
                .anyMatch(line -> line.getLineDiscountAmount() != null
                        && line.getLineDiscountAmount().signum() > 0);

        if (hasLineDiscount && !bill.isNone()) {
            throw new IllegalArgumentException(
                    "ไม่สามารถใช้ส่วนลดรายการร่วมกับส่วนลดทั้งบิลได้"
            );
        }

        BigDecimal billAmount = bill.amountOff(amountBeforeBillDiscount);

        if (lines.stream().anyMatch(line -> line.getId() == null)) {
            throw new IllegalArgumentException(
                    "ต้องบันทึกรายการขายก่อนคำนวณใบเสร็จ"
            );
        }

        lines = lines.stream()
                .sorted(Comparator.comparing(SalesTransaction::getId))
                .toList();

        coursePayments = coursePayments.stream()
                .sorted(Comparator.comparing(item -> item.getCourse().getId()))
                .toList();

        List<BigDecimal> amounts = new ArrayList<>();

        for (SalesTransaction line : lines) {
            amounts.add(line.getAmount());
        }

        for (ReceiptCoursePayment item : coursePayments) {
            amounts.add(item.getAmount());
        }

        List<BigDecimal> shares = allocateBillDiscount(
                amounts, amountBeforeBillDiscount, billAmount
        );

        int index = 0;

        for (SalesTransaction line : lines) {
            BigDecimal share = shares.get(index++);
            line.setAllocatedBillDiscountAmount(share);

            if (line.getTransactionType() == TransactionType.CoursePurchase) {
                courses.findBySaleTransactionId(line.getId()).ifPresent(course -> {
                    course.setTreatmentFeeBaseAmount(
                            line.getAmount().subtract(share)
                    );
                    courses.save(course);
                });
            }
        }

        for (ReceiptCoursePayment item : coursePayments) {
            item.setAllocatedBillDiscountAmount(shares.get(index++));
        }

        BigDecimal taxable = amountBeforeBillDiscount.subtract(billAmount);
        receipt.setSubtotalBeforeDiscount(subtotal);
        receipt.setBillDiscountType(bill.type());
        receipt.setBillDiscountValue(bill.value());
        receipt.setBillDiscountAmount(billAmount);
        receipt.setTaxableAmount(taxable);
        receipt.setTaxAmount(BigDecimal.ZERO.setScale(2));
        receipt.setNetAmount(taxable);
        receipt.setReceiptUpdateAt(Instant.now());
        receipt.setReceiptUpdateBy(actorId);
        return repository.save(receipt);
    }

    @Transactional
    public Receipt markVoided(Receipt receipt, Long actorId) {
        if (receipt.getReceiptStatus() != ReceiptStatus.Issued) {
            throw new IllegalArgumentException("ใบเสร็จนี้ไม่ได้อยู่ในสถานะออกแล้ว");
        }
        receipt.setReceiptStatus(ReceiptStatus.Voided);
        receipt.setReceiptUpdateAt(Instant.now());
        receipt.setReceiptUpdateBy(actorId);
        return repository.save(receipt);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }
        return value.trim();
    }
}
