package com.sabyeguyz.clinicv2.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientVisitRepository extends JpaRepository<PatientVisit, Long> {
}
