package com.sabyeguyz.clinic.therapist;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TherapistRepository extends JpaRepository<Therapist, Long> {

    @Query("select t from Therapist t join fetch t.branch where t.active = true order by t.fullName")
    List<Therapist> findByActiveTrueOrderByFullName();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Therapist t where t.id = :id")
    Optional<Therapist> findByIdForUpdate(@Param("id") Long id);
}
