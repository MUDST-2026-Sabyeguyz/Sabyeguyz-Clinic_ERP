package com.sabyeguyz.clinicv2.commission;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TreatmentFeeRuleRepository extends JpaRepository<TreatmentFeeRule, Long> {
    @Query("select r from TreatmentFeeRule r where r.treatmentFeeRuleStatus = true "
            + "and r.effectiveFrom <= :date and (r.effectiveTo is null or :date <= r.effectiveTo)")
    List<TreatmentFeeRule> findActiveOn(@Param("date") LocalDate date);

    List<TreatmentFeeRule> findAllByOrderByEffectiveFromDesc();

    List<TreatmentFeeRule> findByTreatmentFeeRuleStatusTrue();

    @Query("select count(a) from CommissionAllocationLedger a where a.treatmentFeeRule.id = :id")
    long countAllocationUses(@Param("id") Long id);

    @Query("select count(s) from SingleVisitFeeLedger s where s.treatmentFeeRule.id = :id")
    long countSingleVisitUses(@Param("id") Long id);

    @Query("select count(a) from CommissionAllocationLedger a where a.treatmentFeeRule.id = :id and a.visitDate > :date")
    long countAllocationUsesAfter(@Param("id") Long id, @Param("date") LocalDate date);

    @Query("select count(s) from SingleVisitFeeLedger s where s.treatmentFeeRule.id = :id and s.serviceDate > :date")
    long countSingleVisitUsesAfter(@Param("id") Long id, @Param("date") LocalDate date);

    /** Whether any allocation or single-visit ledger row copied this rule. */
    default boolean isUsedByLedger(Long id) {
        return countAllocationUses(id) + countSingleVisitUses(id) > 0;
    }

    /** Whether a ledger row of this rule is dated after the given day. */
    default boolean isUsedAfter(Long id, LocalDate date) {
        return countAllocationUsesAfter(id, date) + countSingleVisitUsesAfter(id, date) > 0;
    }
}
