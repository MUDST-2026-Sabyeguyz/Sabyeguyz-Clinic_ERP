package com.sabyeguyz.clinicv2.commission;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffMonthlyPaymentRepository extends JpaRepository<StaffMonthlyPayment, Long> {
    java.util.Optional<StaffMonthlyPayment> findByStaffIdAndPaymentMonth(Long staffId, String paymentMonth);

    java.util.List<StaffMonthlyPayment> findByPaymentMonthOrderByIdAsc(String paymentMonth);

    java.util.List<StaffMonthlyPayment> findByStaffIdOrderByPaymentMonthDesc(Long staffId);
}
