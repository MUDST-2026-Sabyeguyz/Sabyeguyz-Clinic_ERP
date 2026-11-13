package com.sabyeguyz.clinicv2.commission;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffMonthlyPaymentRepository extends JpaRepository<StaffMonthlyPayment, Long> {
    java.util.List<StaffMonthlyPayment> findByBranchIdAndPaymentStatusOrderByPaymentMonthDesc(
            Long branchId, com.sabyeguyz.clinicv2.enums.PaymentStatus status);
    java.util.List<StaffMonthlyPayment> findByBranchIdOrderByPaymentMonthDesc(Long branchId);
    java.util.Optional<StaffMonthlyPayment> findByStaffIdAndPaymentMonthAndBranchId(
            Long staffId,
            String paymentMonth,
            Long branchId);

    java.util.List<StaffMonthlyPayment> findByPaymentMonthAndBranchIdOrderByIdAsc(
            String paymentMonth,
            Long branchId);

    java.util.List<StaffMonthlyPayment> findByPaymentMonthOrderByIdAsc(String paymentMonth);

    /** Whether anyone of the branch has already been paid for the month (reopening is closed from then on). */
    boolean existsByPaymentMonthAndBranchIdAndPaymentStatus(
            String paymentMonth,
            Long branchId,
            com.sabyeguyz.clinicv2.enums.PaymentStatus paymentStatus);

    java.util.List<StaffMonthlyPayment> findByStaffIdOrderByPaymentMonthDesc(Long staffId);
}
