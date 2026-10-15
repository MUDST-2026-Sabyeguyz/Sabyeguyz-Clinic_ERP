package com.sabyeguyz.clinicv2.commission;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdjustmentApplicationRepository extends JpaRepository<AdjustmentApplication, Long> {
    java.util.List<AdjustmentApplication> findByMonthlyPaymentIdOrderByIdAsc(Long monthlyPaymentId);

    java.util.List<AdjustmentApplication> findByAdjustmentIdOrderByIdAsc(Long adjustmentId);
}
