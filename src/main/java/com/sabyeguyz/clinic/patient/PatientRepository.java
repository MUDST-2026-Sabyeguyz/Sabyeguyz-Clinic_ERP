package com.sabyeguyz.clinic.patient;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    boolean existsByUsernameOrEmailOrIdentityNumber(String username, String email, String identityNumber);
}
