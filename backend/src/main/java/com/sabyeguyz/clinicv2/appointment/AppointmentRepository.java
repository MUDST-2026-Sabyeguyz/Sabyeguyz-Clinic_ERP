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
            + "and (:staffId is null or a.staff.id = :staffId) "
            + "and (:hasStatus = false or a.appointmentStatus = :status) "
            + "and (:query = '' or lower(a.contactName) like :pattern or a.contactPhone like :pattern "
            + "or lower(p.patientFname) like :pattern or lower(p.patientLname) like :pattern "
            + "or lower(p.patientMiddleName) like :pattern or lower(p.patientNickname) like :pattern "
            + "or p.phone like :pattern or p.patientHn like :pattern "
            + "or lower(concat(p.patientFname, ' ', case when p.patientMiddleName is null then '' "
            + "else concat(p.patientMiddleName, ' ') end, p.patientLname)) like :pattern)")
    Page<Appointment> search(@Param("branchId") Long branchId, @Param("hasDate") boolean hasDate,
                              @Param("date") LocalDate date, @Param("staffId") Long staffId,
                              @Param("hasStatus") boolean hasStatus, @Param("status") AppointmentStatus status,
                              @Param("query") String query, @Param("pattern") String pattern, Pageable pageable);

    @Query("select a from Appointment a where a.appointmentDate between :from and :to "
            + "and (:branchId is null or a.branch.id = :branchId)")
    java.util.List<Appointment> findByAppointmentDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                                               @Param("branchId") Long branchId);
}
