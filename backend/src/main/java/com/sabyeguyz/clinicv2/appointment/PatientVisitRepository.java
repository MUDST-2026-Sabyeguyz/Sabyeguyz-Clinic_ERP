package com.sabyeguyz.clinicv2.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientVisitRepository extends JpaRepository<PatientVisit, Long> {
    Optional<PatientVisit> findFirstByAppointmentIdOrderByIdDesc(Long appointmentId);

    @Query("select v from PatientVisit v where v.visitDate between :from and :to "
            + "and (:branchId is null or v.branch.id = :branchId)")
    List<PatientVisit> findByVisitDateBetween(@Param("from") Instant from, @Param("to") Instant to,
                                               @Param("branchId") Long branchId);

    List<PatientVisit> findByAppointmentIdOrderByIdAsc(Long appointmentId);
}
