package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    boolean existsByStaffIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNotIn(
            Long staffId, LocalDate appointmentDate, LocalTime appointmentTime,
            Collection<AppointmentStatus> excludedStatuses);
}
