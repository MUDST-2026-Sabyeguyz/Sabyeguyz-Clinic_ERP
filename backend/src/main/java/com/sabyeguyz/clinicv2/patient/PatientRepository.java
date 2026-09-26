package com.sabyeguyz.clinicv2.patient;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    Optional<Patient> findByPatientHn(String patientHn);

    @Query("select p from Patient p where "
            + "(:query is null or :query = '' "
            + " or lower(p.patientFname) like lower(concat('%', :query, '%')) "
            + " or lower(p.patientLname) like lower(concat('%', :query, '%')) "
            + " or p.patientHn like concat('%', :query, '%') "
            + " or p.phone like concat('%', :query, '%')) "
            + "and (:branchId is null or p.branch.id = :branchId)")
    Page<Patient> search(@Param("query") String query, @Param("branchId") Long branchId, Pageable pageable);
}
