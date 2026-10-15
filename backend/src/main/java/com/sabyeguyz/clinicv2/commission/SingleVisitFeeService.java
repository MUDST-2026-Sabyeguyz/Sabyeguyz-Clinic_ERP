package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.appointment.PatientVisit;
import com.sabyeguyz.clinicv2.enums.AllocationEntryType;
import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.enums.PaymentStatus;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The treatment fee of a single-visit (non-course) service (Data Dictionary: Single_visit_fee_ledger). It is earned by
 * the one PT who treated the visit once the visit is treated and paid, at the central Treatment Fee Rule in force on the
 * service date:
 * Percentage = ROUND(base x value / 100, 2) where the base is the net paid amount of the single-visit line (amount less
 * its share of the bill discount, never the courses sold or used on the same bill); Fixed = one fee per visit, not
 * multiplied by the number of services. A fee is never edited: before payment a correction is a Reversal plus a new
 * Allocation of the next version; once Paid it can only be fixed with a pay adjustment.
 */
@Service
public class SingleVisitFeeService {
    private final SingleVisitFeeLedgerRepository fees;
    private final TreatmentFeeRuleResolver rules;

    public SingleVisitFeeService(SingleVisitFeeLedgerRepository fees, TreatmentFeeRuleResolver rules) {
        this.fees = fees;
        this.rules = rules;
    }

    /**
     * Records the fee of a treated and paid single visit, if it has none yet and a Treatment Fee Rule applies on the
     * service date (otherwise nothing is recorded: the fee is never taken as 0).
     *
     * @param netPaidBase amount of the single-visit line less its allocated bill discount
     */
    @Transactional
    public Optional<SingleVisitFeeLedger> accrue(PatientVisit visit, Staff treatingStaff, BigDecimal netPaidBase,
                                                  LocalDate serviceDate, Long actorId) {
        if (visit == null || treatingStaff == null) {
            throw new IllegalArgumentException("ต้องระบุ Visit และผู้รักษาของการรักษารายครั้ง");
        }
        if (current(visit).isPresent()) {
            return Optional.empty();
        }
        Optional<TreatmentFeeRule> found = rules.resolve(serviceDate);
        if (found.isEmpty()) {
            return Optional.empty(); // no rule on that day: no fee is made up; it is recorded later (backfill)
        }
        TreatmentFeeRule rule = found.get();
        BigDecimal base = netPaidBase.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal amount = rule.getFeeType() == FeeType.Fixed
                ? rule.getFeeValue().setScale(2, RoundingMode.HALF_UP)
                : base.multiply(rule.getFeeValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        SingleVisitFeeLedger fee = new SingleVisitFeeLedger();
        fee.setVisit(visit);
        fee.setStaff(treatingStaff);
        fee.setTreatmentFeeRule(rule);
        fee.setServiceDate(serviceDate);
        fee.setFeeType(rule.getFeeType());
        fee.setFeeValue(rule.getFeeValue());
        fee.setCalculationBase(base);
        fee.setFeeAmount(amount);
        fee.setEntryType(AllocationEntryType.Allocation);
        fee.setFeeVersion(nextVersion(visit));
        fee.setCreatedAt(Instant.now());
        fee.setCreatedBy(actorId);
        return Optional.of(fees.save(fee));
    }

    /**
     * Reverses the visit's fee (a wrong entry or a voided sale) before it is paid out. A fee already in a Paid payment is
     * left as it is: that is corrected by a pay adjustment in a later period. Returns the Reversal, if one was written.
     */
    @Transactional
    public Optional<SingleVisitFeeLedger> reverse(PatientVisit visit, String reason, Long actorId) {
        Optional<SingleVisitFeeLedger> current = current(visit);
        if (current.isEmpty()) {
            return Optional.empty();
        }
        SingleVisitFeeLedger original = current.get();
        if (original.getMonthlyPayment() != null && original.getMonthlyPayment().getPaymentStatus() == PaymentStatus.Paid) {
            return Optional.empty();
        }
        SingleVisitFeeLedger reversal = new SingleVisitFeeLedger();
        reversal.setVisit(original.getVisit());
        reversal.setStaff(original.getStaff());
        reversal.setTreatmentFeeRule(original.getTreatmentFeeRule());
        reversal.setServiceDate(original.getServiceDate());
        reversal.setFeeType(original.getFeeType());
        reversal.setFeeValue(original.getFeeValue());
        reversal.setCalculationBase(original.getCalculationBase());
        reversal.setFeeAmount(original.getFeeAmount().negate());
        reversal.setEntryType(AllocationEntryType.Reversal);
        reversal.setReversesFee(original);
        reversal.setReason(reason.trim());
        reversal.setFeeVersion(original.getFeeVersion());
        reversal.setCreatedAt(Instant.now());
        reversal.setCreatedBy(actorId);
        return Optional.of(fees.save(reversal));
    }

    /** The fee of the visit that has not been reversed. */
    @Transactional(readOnly = true)
    public Optional<SingleVisitFeeLedger> current(PatientVisit visit) {
        List<SingleVisitFeeLedger> rows = fees.findByVisitIdOrderByIdAsc(visit.getId());
        Set<Long> reversed = new HashSet<>();
        for (SingleVisitFeeLedger row : rows) {
            if (row.getEntryType() == AllocationEntryType.Reversal && row.getReversesFee() != null) {
                reversed.add(row.getReversesFee().getId());
            }
        }
        return rows.stream()
                .filter(row -> row.getEntryType() == AllocationEntryType.Allocation && !reversed.contains(row.getId()))
                .reduce((first, second) -> second);
    }

    private int nextVersion(PatientVisit visit) {
        return fees.findByVisitIdOrderByIdAsc(visit.getId()).stream()
                .mapToInt(SingleVisitFeeLedger::getFeeVersion).max().orElse(0) + 1;
    }
}
