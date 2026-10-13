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

    /**
     * PostgreSQL cannot infer the type of an untyped NULL date/enum parameter in "(? is null or x = ?)",
     * so the optional date and status filters use a boolean flag plus a never-null value instead.
     */
    @Query("select a from Appointment a where "
            + "(:branchId is null or a.branch.id = :branchId) "
            + "and (:hasDate = false or a.appointmentDate = :date) "
            + "and (:staffId is null or a.staff.id = :staffId) "
            + "and (:hasStatus = false or a.appointmentStatus = :status)")
    Page<Appointment> search(@Param("branchId") Long branchId, @Param("hasDate") boolean hasDate,
                              @Param("date") LocalDate date, @Param("staffId") Long staffId,
                              @Param("hasStatus") boolean hasStatus, @Param("status") AppointmentStatus status,
                              Pageable pageable);

    @Query("select a from Appointment a where a.appointmentDate between :from and :to "
            + "and (:branchId is null or a.branch.id = :branchId)")
    java.util.List<Appointment> findByAppointmentDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                                               @Param("branchId") Long branchId);
}
