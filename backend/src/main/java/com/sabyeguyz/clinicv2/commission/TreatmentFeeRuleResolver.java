package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.enums.CalculationBase;
import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks and applies a Treatment Fee Rule (Commission_System_Requirement_Dev
 * §15.1). This fee is what the substitute Treating PT is paid when they are
 * not the Course's Case Owner — never a Commission Tier for them.
 *
 * <p>Known simplification: rule matching here only considers a rule's own
 * {@code staff} field, not {@code staffGroup}/{@code serviceGroup}
 * membership (Staff_group_member/Service_group_member joins). A rule with
 * a specific staff wins over one with no staff (a default/catch-all rule);
 * group-scoped rules are not yet resolved and are skipped. Add that once
 * group membership lookups are needed.</p>
 */
@Service
public class TreatmentFeeRuleResolver {
    private final TreatmentFeeRuleRepository rules;

    public TreatmentFeeRuleResolver(TreatmentFeeRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public Optional<TreatmentFeeRule> resolve(Staff treatingStaff, LocalDate date) {
        List<TreatmentFeeRule> active = rules.findActiveOn(date);
        return active.stream()
                .filter(rule -> rule.getStaff() == null
                        || rule.getStaff().getId().equals(treatingStaff.getId()))
                .max(Comparator.comparingInt(rule -> rule.getStaff() != null ? 1 : 0));
    }

    /** THB amount to pay the substitute Treating PT for one visit of the given Course. */
    public BigDecimal calculateFee(TreatmentFeeRule rule, Course course) {
        if (rule.getFeeType() == FeeType.Fixed) {
            return rule.getFeeValue().setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal base = calculationBase(rule, course);
        return base.multiply(rule.getFeeValue()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculationBase(TreatmentFeeRule rule, Course course) {
        CalculationBase base = rule.getPercentageBase();
        if (base == null || base == CalculationBase.CourseValuePerVisit) {
            return course.getCoursePrice().divide(BigDecimal.valueOf(course.getTotalVisits()), 4, RoundingMode.HALF_UP);
        }
        throw new IllegalStateException("ยังไม่รองรับ percentage_base: " + base);
    }
}
