package com.sabyeguyz.clinic.treatment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TreatmentRepository extends JpaRepository<Treatment, Long> {
    List<Treatment> findByPatientIdOrderByCreatedAtDesc(Long patientId);

    Page<Treatment> findByPatientId(Long patientId, Pageable pageable);
}
