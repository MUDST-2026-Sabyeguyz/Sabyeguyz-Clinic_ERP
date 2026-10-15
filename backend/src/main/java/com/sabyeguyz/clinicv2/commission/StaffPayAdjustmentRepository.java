package com.sabyeguyz.clinicv2.commission;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffPayAdjustmentRepository extends JpaRepository<StaffPayAdjustment, Long> {
    java.util.List<StaffPayAdjustment> findAllByOrderByIdDesc();

    java.util.List<StaffPayAdjustment> findByStaffIdOrderByIdDesc(Long staffId);
}
