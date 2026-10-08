package com.sabyeguyz.clinicv2.treatment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {
    List<Treatment> findByVisit_Patient_IdOrderByTreatmentCreateAtDesc(Long patientId);
}
