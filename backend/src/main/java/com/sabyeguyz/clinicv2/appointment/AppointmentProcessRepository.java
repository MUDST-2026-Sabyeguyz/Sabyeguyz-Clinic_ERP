package com.sabyeguyz.clinicv2.appointment;

public interface AppointmentProcessRepository extends org.springframework.data.jpa.repository.JpaRepository<AppointmentProcess, Long> {
    boolean existsByReceiptIdAndAppointmentIdNot(Long receiptId, Long appointmentId);
    boolean existsByUsageIdAndAppointmentIdNot(Long usageId, Long appointmentId);
}
