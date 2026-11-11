package com.sabyeguyz.clinicv2.report;

import com.sabyeguyz.clinicv2.branch.BranchRepository;
import com.sabyeguyz.clinicv2.enums.ReceiptStatus;
import com.sabyeguyz.clinicv2.enums.RefundStatus;
import com.sabyeguyz.clinicv2.sale.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Detailed, unpaginated report sources: one shared filter contract for screen and CSV. */
@RestController
@PreAuthorize("hasRole('MANAGER')")
@Transactional(readOnly = true)
public class ReportTransactionsController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");
    private final ReceiptRepository receipts;
    private final SalesTransactionRepository sales;
    private final PaymentTransactionRepository payments;
    private final ReceiptCoursePaymentRepository collections;
    private final RefundTransactionRepository refunds;
    private final BranchRepository branches;

    public ReportTransactionsController(ReceiptRepository receipts, SalesTransactionRepository sales,
            PaymentTransactionRepository payments, ReceiptCoursePaymentRepository collections,
            RefundTransactionRepository refunds, BranchRepository branches) {
        this.receipts = receipts; this.sales = sales; this.payments = payments;
        this.collections = collections; this.refunds = refunds; this.branches = branches;
    }

    public record Row(String source, Long sourceId, Long receiptId, String receiptNo,
            Long patientId, String patientName, String patientHn, String branchCode, String branchName,
            LocalDate date, String type, String treatment, String paymentMethod, BigDecimal amount) {}
    public record MoneyOut(Long refundId, Long originalPaymentId, String receiptNo, LocalDate date,
            String paymentMethod, BigDecimal amount) {}
    public record Result(String basis, LocalDate from, LocalDate to, List<Row> rows, BigDecimal total,
            Map<String, BigDecimal> byMethod, List<MoneyOut> refunds, BigDecimal refundedTotal) {}
    private record Item(String type, String name, BigDecimal payable, Long id, String source) {}

    @GetMapping("/api/reports/sales-detail")
    public Result sales(@RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) String branchCode,
            @RequestParam(defaultValue = "") String patient,
            @RequestParam(defaultValue = "") String saleType,
            @RequestParam(defaultValue = "") String treatment) {
        Long branchId = branch(branchCode); range(from, to);
        List<Row> rows = new ArrayList<>();
        for (Receipt receipt : receipts.findBySaleDateBetween(from, to, branchId)) {
            if (receipt.getReceiptStatus() == ReceiptStatus.Voided) continue;
            // Collection receipts contain no new sale: never count them as new turnover.
            for (SalesTransaction line : sales.findByReceiptId(receipt.getId())) {
                if (!line.isTransactionStatus()) continue;
                Item item = new Item(line.getTransactionType().name(), line.getItemNameSnapshot(),
                        line.getAmount().subtract(line.getAllocatedBillDiscountAmount()), line.getId(), "SALE");
                Row row = row(receipt, receipt.getSaleDate().toLocalDate(), item, "", item.payable());
                if (matches(row, patient, saleType, treatment)) rows.add(row);
            }
        }
        return result("SALE_DATE", from, to, rows, List.of());
    }

    @GetMapping("/api/reports/payments-detail")
    public Result payments(@RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(required = false) String branchCode,
            @RequestParam(defaultValue = "") String patient,
            @RequestParam(defaultValue = "") String saleType,
            @RequestParam(defaultValue = "") String treatment) {
        Long branchId = branch(branchCode); range(from, to);
        List<Row> rows = new ArrayList<>();
        for (PaymentTransaction payment : payments.findByPaymentDateBetween(
                from.atStartOfDay(ZONE).toInstant(), to.plusDays(1).atStartOfDay(ZONE).toInstant(), branchId)) {
            if (!payment.isPaymentStatus() || payment.getReceipt().getReceiptStatus() == ReceiptStatus.Voided) continue;
            List<Item> items = items(payment.getReceipt().getId());
            BigDecimal total = items.stream().map(Item::payable).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal remaining = payment.getAmount();
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                BigDecimal part = i == items.size() - 1 ? remaining : total.signum() == 0 ? BigDecimal.ZERO
                        : payment.getAmount().multiply(item.payable()).divide(total, 2, RoundingMode.HALF_UP);
                remaining = remaining.subtract(part);
                Row row = row(payment.getReceipt(), LocalDate.ofInstant(payment.getPaymentDate(), ZONE),
                        item, payment.getPaymentMethod().getMethodName(), part);
                if (matches(row, patient, saleType, treatment)) rows.add(row);
            }
        }
        List<MoneyOut> moneyOut = new ArrayList<>();
        for (RefundTransaction refund : refunds.findAll()) {
            if (refund.getRefundStatus() != RefundStatus.Completed || refund.getRefundedAt() == null) continue;
            var payment = refund.getOriginalPayment();
            if (branchId != null && !branchId.equals(payment.getBranch().getId())) continue;
            LocalDate date = LocalDate.ofInstant(refund.getRefundedAt(), ZONE);
            if (date.isBefore(from) || date.isAfter(to)) continue;
            // Match the original source as well; refunds stay a separate money-out series.
            if (items(payment.getReceipt().getId()).stream().noneMatch(item -> matches(
                    row(payment.getReceipt(), date, item, "", BigDecimal.ZERO), patient, saleType, treatment))) continue;
            moneyOut.add(new MoneyOut(refund.getId(), payment.getId(), payment.getReceipt().getReceiptNo(), date,
                    refund.getPaymentMethod().getMethodName(), refund.getRefundAmount()));
        }
        return result("PAYMENT_DATE", from, to, rows, moneyOut);
    }

    private List<Item> items(Long receiptId) {
        List<Item> items = new ArrayList<>();
        for (SalesTransaction line : sales.findByReceiptId(receiptId)) {
            if (line.isTransactionStatus()) items.add(new Item(line.getTransactionType().name(), line.getItemNameSnapshot(),
                    line.getAmount().subtract(line.getAllocatedBillDiscountAmount()), line.getId(), "SALE"));
        }
        for (ReceiptCoursePayment item : collections.findByReceipt_IdOrderByCourse_IdAsc(receiptId)) {
            items.add(new Item("CoursePurchase", item.getItemNameSnapshot(),
                    item.getAmount().subtract(item.getAllocatedBillDiscountAmount()), item.getCourse().getId(), "COURSE_COLLECTION"));
        }
        return items;
    }

    private Row row(Receipt receipt, LocalDate date, Item item, String method, BigDecimal amount) {
        var person = receipt.getPatient(); var branch = receipt.getBranch();
        return new Row(item.source(), item.id(), receipt.getId(), receipt.getReceiptNo(), person.getId(),
                (person.getPatientFname() + " " + person.getPatientLname()).trim(), person.getPatientHn(),
                branch.getBranchCode(), branch.getBranchName(), date, item.type(), item.name(), method,
                amount.setScale(2, RoundingMode.HALF_UP));
    }
    static boolean matches(Row row, String patient, String saleType, String treatment) {
        return contains(row.patientName() + " " + row.patientHn(), patient)
                && contains(row.type(), saleType) && contains(row.treatment(), treatment);
    }
    private static boolean contains(String source, String query) {
        return query == null || query.isBlank() || source.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }
    private Long branch(String code) {
        if (code == null || code.isBlank()) return null;
        return branches.findByBranchCode(code.trim()).orElseThrow(() -> new IllegalArgumentException("ไม่พบสาขา")).getId();
    }
    private static void range(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new IllegalArgumentException("วันสิ้นสุดต้องไม่ก่อนวันเริ่มต้น");
    }
    private static Result result(String basis, LocalDate from, LocalDate to, List<Row> rows, List<MoneyOut> refunds) {
        rows.sort(Comparator.comparing(Row::date).thenComparing(Row::receiptId).thenComparing(Row::sourceId));
        Map<String, BigDecimal> methods = new LinkedHashMap<>();
        rows.forEach(row -> { if (!row.paymentMethod().isBlank()) methods.merge(row.paymentMethod(), row.amount(), BigDecimal::add); });
        return new Result(basis, from, to, List.copyOf(rows),
                rows.stream().map(Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add), methods, List.copyOf(refunds),
                refunds.stream().map(MoneyOut::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
