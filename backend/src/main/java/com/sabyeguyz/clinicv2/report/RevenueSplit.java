package com.sabyeguyz.clinicv2.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.sale.ReceiptCoursePayment;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;

/**
 * Payments belong to a receipt, not to one sale line, so revenue by sale type
 * splits a payment across the receipt's lines in proportion to what each line
 * costs after its share of the bill discount; the last line takes the rounding
 * remainder so the parts always add up to the payment.
 */
public final class RevenueSplit {
    private RevenueSplit() {}

    private record RevenueItem(
            TransactionType type,
            BigDecimal payable) {}

    public static Map<TransactionType, BigDecimal> split(
            BigDecimal payment,
            List<SalesTransaction> lines) {
        return split(payment, lines, List.of());
    }

    public static Map<TransactionType, BigDecimal> split(
            BigDecimal payment,
            List<SalesTransaction> lines,
            List<ReceiptCoursePayment> collectionItems) {
        List<RevenueItem> items = new ArrayList<>();

        for (SalesTransaction line : lines) {
            items.add(new RevenueItem(line.getTransactionType(), payable(line)));
        }

        for (ReceiptCoursePayment item : collectionItems) {
            items.add(new RevenueItem(
                    TransactionType.CoursePurchase,
                    item.getAmount().subtract(item.getAllocatedBillDiscountAmount())));
        }

        return splitItems(payment, items);
    }

    private static Map<TransactionType, BigDecimal> splitItems(
            BigDecimal payment,
            List<RevenueItem> items) {
        Map<TransactionType, BigDecimal> result = new EnumMap<>(TransactionType.class);

        if (items.isEmpty()) {
            result.put(TransactionType.SingleVisit, payment);

            return result;
        }

        BigDecimal total = items.stream()
                .map(RevenueItem::payable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = payment;

        for (int i = 0; i < items.size(); i++) {
            RevenueItem item = items.get(i);
            BigDecimal part;

            if (i == items.size() - 1) {
                part = remaining;
            } else if (total.signum() == 0) {
                part = BigDecimal.ZERO.setScale(2);
            } else {
                part = payment.multiply(item.payable()).divide(total, 2, RoundingMode.HALF_UP);
            }

            result.merge(item.type(), part, BigDecimal::add);
            remaining = remaining.subtract(part);
        }

        return result;
    }

    private static BigDecimal payable(SalesTransaction line) {
        return line.getAmount().subtract(line.getAllocatedBillDiscountAmount());
    }
}
