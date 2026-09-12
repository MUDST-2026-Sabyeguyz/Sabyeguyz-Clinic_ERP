package com.sabyeguyz.clinic.commission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.*;

public interface CommissionClosingRepository extends JpaRepository<CommissionClosing, Long> {
    List<CommissionClosing> findByMonthOrderById(LocalDate month);

    Optional<CommissionClosing> findByMonthAndEmployeeId(LocalDate month, Long employeeId);
}
