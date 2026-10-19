package com.sabyeguyz.clinicv2.sale;

import com.sabyeguyz.clinicv2.commission.SingleVisitFeeService;
import com.sabyeguyz.clinicv2.config.ClinicTime;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Earns the treatment fee of the single visits on a receipt once the receipt is paid in full. When no Treatment Fee Rule
 * applies on the service date the sale is not blocked and no fee is invented (never 0): the visit stays without a fee until
 * {@link #backfill} runs after the Manager has set the rule.
 */
@Service
public class SingleVisitFeeAccrual {
    private final SingleVisitFeeService fees;
    private final PaymentTransactionRepository payments;
    private final SalesTransactionRepository transactions;

    public SingleVisitFeeAccrual(SingleVisitFeeService fees, PaymentTransactionRepository payments,
                                  SalesTransactionRepository transactions) {
        this.fees = fees;
        this.payments = payments;
        this.transactions = transactions;
    }

    /** Fees for the single-visit lines of a receipt that is paid in full; returns how many were recorded. */
    @Transactional
    public int accrue(Receipt receipt, List<SalesTransaction> lines, Long actorId) {
        BigDecimal paid = payments.paidAmountOf(receipt.getId());
        if (paid == null || receipt.getNetAmount() == null || paid.compareTo(receipt.getNetAmount()) < 0) {
            return 0;
        }
        int recorded = 0;
        for (SalesTransaction line : lines) {
            if (line.getTransactionType() == TransactionType.SingleVisit && line.isTransactionStatus()
                    && line.getVisit() != null && line.getTreatingStaff() != null) {
                LocalDate serviceDate = ClinicTime.dateOf(line.getVisit().getVisitDate());
                if (fees.accrue(line.getVisit(), line.getTreatingStaff(),
                        line.getAmount().subtract(line.getAllocatedBillDiscountAmount()), serviceDate, actorId).isPresent()) {
                    recorded++;
                }
            }
        }
        return recorded;
    }

    /** Records the fees that are still missing for paid single visits of the period (for example after a rule was added). */
    @Transactional
    public int backfill(LocalDate from, LocalDate to, Long actorId) {
        ZoneId zone = ClinicTime.ZONE;
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        int recorded = 0;
        for (SalesTransaction line : transactions.findByTransactionDateBetween(start, end, null)) {
            if (line.getTransactionType() == TransactionType.SingleVisit) {
                recorded += accrue(line.getReceipt(), List.of(line), actorId);
            }
        }
        return recorded;
    }
}
