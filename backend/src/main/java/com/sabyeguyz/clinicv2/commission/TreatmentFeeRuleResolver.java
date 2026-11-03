package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.enums.FeeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks and applies the Treatment Fee Rule (Data Dictionary: one central rule for every PT and service). The fee is
 * what the treating PT is paid when the Owner of the course does not treat the visit, or for a single visit; it is
 * never part of a Commission Tier.
 */
@Service
public class TreatmentFeeRuleResolver {
    private final TreatmentFeeRuleRepository rules;

    public TreatmentFeeRuleResolver(TreatmentFeeRuleRepository rules) {
        this.rules = rules;
    }

    /** Finds the rule effective on the given date and rejects overlapping rules. */
    @Transactional(readOnly = true)
    public Optional<TreatmentFeeRule> resolve(LocalDate date) {
        List<TreatmentFeeRule> active = rules.findActiveOn(date);

        if (active.size() > 1) {
            throw new IllegalStateException(
                    "พบกฎค่ามือที่มีผลในวันที่ " + date + " มากกว่าหนึ่งรายการ");
        }

        return active.stream().findFirst();
    }

    /** THB amount to pay the substitute Treating PT for one visit of the given Course. */
    public BigDecimal calculateFee(TreatmentFeeRule rule, Course course) {
        if (rule.getFeeType() == FeeType.Fixed) {
            return rule.getFeeValue().setScale(2, RoundingMode.HALF_UP);
        }
        // net course amount x percentage / (total visits x 100), rounded once at the end
        return course.getTreatmentFeeBaseAmount().multiply(rule.getFeeValue())
                .divide(BigDecimal.valueOf(course.getTotalVisits()).multiply(BigDecimal.valueOf(100)), 2, RoundingMode.HALF_UP);
    }

    /** Per-visit base before rounding, kept on the ledger for audit (percentage rules only). */
    public BigDecimal perVisitBase(Course course) {
        return course.getTreatmentFeeBaseAmount().divide(BigDecimal.valueOf(course.getTotalVisits()), 4, RoundingMode.HALF_UP);
    }
}
