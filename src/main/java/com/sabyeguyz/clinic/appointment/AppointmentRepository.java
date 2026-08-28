package com.sabyeguyz.clinic.appointment;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
            select count(a) > 0 from Appointment a
            where a.therapist.id = :therapistId
              and a.status <> :excludedStatus
              and a.startAt < :endAt
              and a.endAt > :startAt
            """)
    boolean existsOverlapping(@Param("therapistId") Long therapistId,
                              @Param("startAt") Instant startAt,
                              @Param("endAt") Instant endAt,
                              @Param("excludedStatus") AppointmentStatus excludedStatus);
}