package com.sabyeguyz.clinicv2.patient;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByPatientHn(String patientHn);
}
