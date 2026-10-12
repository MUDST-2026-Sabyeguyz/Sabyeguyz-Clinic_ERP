package com.sabyeguyz.clinicv2.commission;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyCommissionClosingRepository extends JpaRepository<MonthlyCommissionClosing, Long> {
    Optional<MonthlyCommissionClosing> findByClosingMonthAndStaffId(String closingMonth, Long staffId);

    List<MonthlyCommissionClosing> findByClosingMonth(String closingMonth);
}
