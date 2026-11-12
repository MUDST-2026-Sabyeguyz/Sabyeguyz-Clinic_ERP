package com.sabyeguyz.clinicv2.staff;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    Optional<Staff> findByStaffCode(String staffCode);

<<<<<<< HEAD
=======
    long countByStaffCodeStartingWith(String prefix);

>>>>>>> 3c7da8a915aea6793ee49626494196b33cc60e17
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Staff s where s.id = :id")
    Optional<Staff> findByIdForUpdate(@Param("id") Long id);
}
