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
}
