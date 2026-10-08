package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.patient.Patient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receipts (Clinic ERP Workflow Brief Section 5).
 *
 * <p>Receipt numbers are RE- plus seven digits. The data dictionary has no
 * dedicated receipt-numbering sequence table (unlike HN_sequence for
 * patients), so {@link #nextReceiptNo()} takes the highest number in use plus
 * one; it is not safe under concurrent requests and needs a locked sequence
 * before this goes live.</p>
 *
 * <p>Tax policy is still to be confirmed with the business, so no tax is
 * charged: taxable_amount is the amount after the bill discount, tax_amount is
 * zero and net_amount equals taxable_amount.</p>
 */
@Service
public class ReceiptService {
    private static final String RECEIPT_NO_FORMAT = "^RE-[0-9]{7}$";

    private final ReceiptRepository repository;

    public ReceiptService(ReceiptRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public String nextReceiptNo() {
        return "RE-%07d".formatted(repository.findMaxReceiptSequence() + 1);
    }

    @Transactional
    public Receipt create(String receiptNo, Patient patient, Branch branch, LocalDate saleDate, Long actorId) {
        String no = requireText(receiptNo, "receiptNo");
        if (!no.matches(RECEIPT_NO_FORMAT)) {
            throw new IllegalArgumentException("เลขที่ใบเสร็จต้องอยู่ในรูปแบบ RE-xxxxxxx");
        }
        if (repository.findByReceiptNo(no).isPresent()) {
            throw new IllegalArgumentException("เลขที่ใบเสร็จซ้ำ");
        }
        if (patient == null) {
            throw new IllegalArgumentException("ต้องระบุผู้ป่วย");
        }
        if (branch == null) {
            throw new IllegalArgumentException("ต้องระบุสาขา");
        }
        Receipt receipt = new Receipt();
        receipt.setReceiptNo(no);
        receipt.setPatient(patient);
        receipt.setBranch(branch);
        receipt.setSaleDate(saleDate == null ? LocalDateTime.now() : saleDate.atStartOfDay());
        receipt.setReceiptCreateAt(Instant.now());
        receipt.setReceiptCreateBy(actorId);
        return repository.save(receipt);
    }

    /**
     * Totals a receipt from its lines: subtotal is the sum of the line amounts
     * (already after line discounts), the bill discount comes off that subtotal
     * and is allocated back onto the lines in proportion to their amounts, the
     * last line taking the rounding remainder. A bill discount never changes a
     * line's commissionable amount.
     */
    @Transactional
    public Receipt finalizeTotals(Receipt receipt, List<SalesTransaction> lines, Discount billDiscount, Long actorId) {
        if (receipt == null) {
            throw new IllegalArgumentException("ต้องระบุใบเสร็จ");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("ใบเสร็จต้องมีรายการอย่างน้อย 1 รายการ");
        }
        Discount bill = billDiscount == null ? Discount.none() : billDiscount;
        BigDecimal subtotal = lines.stream().map(SalesTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal billAmount = bill.amountOff(subtotal);

        BigDecimal remaining = billAmount;
        for (int i = 0; i < lines.size(); i++) {
            SalesTransaction line = lines.get(i);
            BigDecimal share = i == lines.size() - 1 ? remaining
                    : subtotal.signum() == 0 ? BigDecimal.ZERO.setScale(2)
                    : billAmount.multiply(line.getAmount()).divide(subtotal, 2, RoundingMode.HALF_UP);
            line.setAllocatedBillDiscountAmount(share);
            remaining = remaining.subtract(share);
        }

        BigDecimal taxable = subtotal.subtract(billAmount);
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

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }
        return value.trim();
    }
}
