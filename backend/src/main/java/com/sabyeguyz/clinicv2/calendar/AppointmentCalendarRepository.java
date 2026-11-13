package com.sabyeguyz.clinicv2.calendar;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sabyeguyz.clinicv2.appointment.Appointment;

/** Queries of the calendar sync; kept apart from AppointmentRepository so the booking code is untouched. */
public interface AppointmentCalendarRepository extends JpaRepository<Appointment, Long> {
    /**
     * Appointments of branches that have a calendar and are not in step with it: never synced, or changed
     * after the last sync (a failed sync leaves the last-synced time alone, so it is retried too).
     */
    @Query("select a.id from Appointment a where a.branch.googleCalendarId is not null "
            + "and a.appointmentDate >= :since "
            + "and (a.calendarLastSyncedAt is null or a.appointmentUpdateAt > a.calendarLastSyncedAt) order by a.id")
    List<Long> findDirtyIds(
            @Param("since") LocalDate since,
            Pageable pageable);

    @Query("select count(a) from Appointment a where a.branch.id = :branchId and a.appointmentDate >= :since "
            + "and (a.calendarLastSyncedAt is null or a.appointmentUpdateAt > a.calendarLastSyncedAt)")
    long countDirty(
            @Param("branchId") Long branchId,
            @Param("since") LocalDate since);

    @Query("select a from Appointment a join fetch a.branch left join fetch a.patient left join fetch a.service "
            + "join fetch a.staff where a.id = :id")
    Optional<Appointment> findForSync(
            @Param("id") Long id);

    @Query(value = "select count(*) from appointment where branch_id = :branchId and calendar_sync_status::text = 'Failed'",
            nativeQuery = true)
    long countFailed(
            @Param("branchId") Long branchId);
}
