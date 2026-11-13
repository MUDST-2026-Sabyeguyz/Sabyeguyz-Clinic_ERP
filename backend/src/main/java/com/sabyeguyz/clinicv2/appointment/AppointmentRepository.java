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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    java.util.Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    boolean existsByStaffIdAndAppointmentDateAndAppointmentTimeAndIdNotAndAppointmentStatusNotIn(
            Long staffId, LocalDate date, LocalTime time, Long excludedId, Collection<AppointmentStatus> statuses);
    boolean existsByStaffIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNotIn(
            Long staffId, LocalDate appointmentDate, LocalTime appointmentTime,
            Collection<AppointmentStatus> excludedStatuses);

    /**
     * PostgreSQL cannot infer the type of an untyped NULL date/enum parameter in "(? is null or x = ?)",
     * so the optional date and status filters use a boolean flag plus a never-null value instead.
     */
    @Query("select a from Appointment a left join a.patient p where "
            + "(:branchId is null or a.branch.id = :branchId) "
            + "and (:hasDate = false or a.appointmentDate = :date) "
            + "and (:hasStartDate = false or a.appointmentDate >= :startDate) "
            + "and (:hasEndDate = false or a.appointmentDate <= :endDate) "
            + "and (:staffId is null or a.staff.id = :staffId) "
            + "and (:hasStatus = false or a.appointmentStatus = :status) "
            + "and (:query = '' or lower(a.contactName) like :pattern or a.contactPhone like :pattern "
            + "or lower(p.patientFname) like :pattern or lower(p.patientLname) like :pattern "
            + "or lower(p.patientMiddleName) like :pattern or lower(p.patientNickname) like :pattern "
            + "or p.phone like :pattern or p.patientHn like :pattern "
            + "or lower(concat(p.patientFname, ' ', case when p.patientMiddleName is null then '' "
            + "else concat(p.patientMiddleName, ' ') end, p.patientLname)) like :pattern)")
    Page<Appointment> search(@Param("branchId") Long branchId, @Param("hasDate") boolean hasDate,
                              @Param("date") LocalDate date, @Param("hasStartDate") boolean hasStartDate,
                              @Param("startDate") LocalDate startDate, @Param("hasEndDate") boolean hasEndDate,
                              @Param("endDate") LocalDate endDate, @Param("staffId") Long staffId,
                              @Param("hasStatus") boolean hasStatus, @Param("status") AppointmentStatus status,
                              @Param("query") String query, @Param("pattern") String pattern, Pageable pageable);

    /** The appointments that still hold a therapist's time on a day (status passed as a parameter, never a literal). */
    @Query("select a from Appointment a where a.staff.id = :staffId and a.appointmentDate = :date "
            + "and a.appointmentStatus not in :released")
    java.util.List<Appointment> activeOfStaffOnDate(@Param("staffId") Long staffId, @Param("date") LocalDate date,
                                                     @Param("released") Collection<AppointmentStatus> released);

    @Query("select a from Appointment a "
            + "where a.staff.id = :staffId "
            + "and a.appointmentDate <= :endDate "
            + "and a.appointmentEndAt > :startAt "
            + "and a.appointmentStatus not in :released")
    java.util.List<Appointment> activeOfStaffForInterval(
            @Param("staffId") Long staffId,
            @Param("startAt") java.time.LocalDateTime startAt,
            @Param("endDate") LocalDate endDate,
            @Param("released") Collection<AppointmentStatus> released);

    @Query("select a from Appointment a where a.appointmentDate between :from and :to "
            + "and (:branchId is null or a.branch.id = :branchId)")
    java.util.List<Appointment> findByAppointmentDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                                               @Param("branchId") Long branchId);
}
