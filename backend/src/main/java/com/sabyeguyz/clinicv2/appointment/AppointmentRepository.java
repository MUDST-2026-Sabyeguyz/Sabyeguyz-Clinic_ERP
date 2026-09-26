package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    boolean existsByStaffIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNotIn(
            Long staffId, LocalDate appointmentDate, LocalTime appointmentTime,
            Collection<AppointmentStatus> excludedStatuses);

    @Query("select a from Appointment a where "
            + "(:branchId is null or a.branch.id = :branchId) "
            + "and (:date is null or a.appointmentDate = :date) "
            + "and (:staffId is null or a.staff.id = :staffId) "
            + "and (:status is null or a.appointmentStatus = :status)")
    Page<Appointment> search(@Param("branchId") Long branchId, @Param("date") LocalDate date,
                              @Param("staffId") Long staffId, @Param("status") AppointmentStatus status,
                              Pageable pageable);
}
