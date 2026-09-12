package com.sabyeguyz.clinic.commission;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.*;

public interface TreatmentFeeRuleRepository extends JpaRepository<TreatmentFeeRule, Long> {
    @Query(
            "select r from TreatmentFeeRule r where r.employee.id=:employee and r.active=true and"
                + " r.effectiveFrom<=:date and (r.effectiveTo is null or r.effectiveTo>:date) order"
                + " by r.effectiveFrom desc")
    List<TreatmentFeeRule> findCurrentRows(
            @Param("employee") Long employee, @Param("date") LocalDate date);

    default Optional<TreatmentFeeRule> findCurrent(Long employee, LocalDate date) {
        return findCurrentRows(employee, date).stream().findFirst();
    }
}
