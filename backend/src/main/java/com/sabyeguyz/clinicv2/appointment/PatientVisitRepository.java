package com.sabyeguyz.clinicv2.appointment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientVisitRepository extends JpaRepository<PatientVisit, Long> {
    Optional<PatientVisit> findFirstByAppointmentIdOrderByIdDesc(Long appointmentId);
}
