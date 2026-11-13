package com.sabyeguyz.clinicv2.report;

import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.sale.SalesTransaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Payments belong to a receipt, not to one sale line, so revenue by sale type
 * splits a payment across the receipt's lines in proportion to what each line
 * costs after its share of the bill discount; the last line takes the rounding
 * remainder so the parts always add up to the payment.
 */
public final class RevenueSplit {
    private RevenueSplit() {}

    public static Map<TransactionType, BigDecimal> split(BigDecimal payment, List<SalesTransaction> lines) {
        Map<TransactionType, BigDecimal> result = new EnumMap<>(TransactionType.class);
        if (lines.isEmpty()) {
            result.put(TransactionType.SingleVisit, payment);
            return result;
        }
        BigDecimal total = lines.stream().map(RevenueSplit::payable).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = payment;
        for (int i = 0; i < lines.size(); i++) {
            SalesTransaction line = lines.get(i);
            BigDecimal part;
            if (i == lines.size() - 1) {
                part = remaining;
            } else if (total.signum() == 0) {
                part = BigDecimal.ZERO.setScale(2);
            } else {
                part = payment.multiply(payable(line)).divide(total, 2, RoundingMode.HALF_UP);
            }
            result.merge(line.getTransactionType(), part, BigDecimal::add);
            remaining = remaining.subtract(part);
        }
        return result;
    }

    private static BigDecimal payable(SalesTransaction line) {
        return line.getAmount().subtract(line.getAllocatedBillDiscountAmount());
    }
}
