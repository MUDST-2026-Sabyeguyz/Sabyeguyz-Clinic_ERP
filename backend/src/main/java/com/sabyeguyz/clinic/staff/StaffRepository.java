package com.sabyeguyz.clinic.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    List<Staff> findAllByOrderByFullName();

    Optional<Staff> findByEmployeeCode(String employeeCode);
}
