package com.sabyeguyz.clinicv2.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentStatusHistoryRepository extends JpaRepository<AppointmentStatusHistory, Long> {
    java.util.Optional<AppointmentStatusHistory> findFirstByAppointmentIdAndToStatusOrderByChangedAtDescIdDesc(
            Long appointmentId,
            com.sabyeguyz.clinicv2.enums.AppointmentStatus toStatus);
}
