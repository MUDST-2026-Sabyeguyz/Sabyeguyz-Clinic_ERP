package com.sabyeguyz.clinic.patient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    boolean existsByUsernameOrEmailOrIdentityNumber(String username, String email, String identityNumber);
    boolean existsByIdentityNumberAndIdNot(String identityNumber, Long id);

    @Query("""
            select p from Patient p
            where :query = ''
               or lower(p.hn) like lower(concat('%', :query, '%'))
               or lower(p.fullName) like lower(concat('%', :query, '%'))
               or lower(p.phone) like lower(concat('%', :query, '%'))
               or lower(p.identityNumber) like lower(concat('%', :query, '%'))
            """)
    Page<Patient> search(@Param("query") String query, Pageable pageable);
}
