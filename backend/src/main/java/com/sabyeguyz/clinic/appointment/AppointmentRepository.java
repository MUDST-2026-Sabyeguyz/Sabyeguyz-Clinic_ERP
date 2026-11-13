package com.sabyeguyz.clinic.appointment;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.therapist.id = :therapistId
              and a.status not in :excludedStatuses
              and a.startAt < :endAt
              and a.endAt > :startAt
            """)
    boolean existsOverlapping(
            @Param("therapistId") Long therapistId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excludedStatuses") Collection<AppointmentStatus> excludedStatuses);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.therapist.id = :therapistId
              and a.id <> :excludeAppointmentId
              and a.status not in :excludedStatuses
              and a.startAt < :endAt
              and a.endAt > :startAt
            """)
    boolean existsOverlappingExcludingAppointment(
            @Param("therapistId") Long therapistId,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("excludedStatuses") Collection<AppointmentStatus> excludedStatuses,
            @Param("excludeAppointmentId") Long excludeAppointmentId);

    @Query(value = """
            select a from Appointment a
              join fetch a.branch
              join fetch a.patient
              join fetch a.therapist
              join fetch a.service
            where (cast(:branchId as Long) is null or a.branch.id = :branchId)
              and (cast(:therapistId as Long) is null or a.therapist.id = :therapistId)
              and (cast(:status as String) is null or a.status = :status)
              and (cast(:from as Instant) is null or a.startAt >= :from)
              and (cast(:to as Instant) is null or a.startAt < :to)
            """,
            countQuery = """
                    select count(a) from Appointment a
                    where (cast(:branchId as Long) is null or a.branch.id = :branchId)
                      and (cast(:therapistId as Long) is null or a.therapist.id = :therapistId)
                      and (cast(:status as String) is null or a.status = :status)
                      and (cast(:from as Instant) is null or a.startAt >= :from)
                      and (cast(:to as Instant) is null or a.startAt < :to)
                    """)
    Page<Appointment> search(
            @Param("branchId") Long branchId,
            @Param("therapistId") Long therapistId,
            @Param("status") AppointmentStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
