package com.sabyeguyz.clinic.therapist;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TherapistRepository extends JpaRepository<Therapist, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Therapist t where t.id = :id")
    Optional<Therapist> findByIdForUpdate(@Param("id") Long id);
}