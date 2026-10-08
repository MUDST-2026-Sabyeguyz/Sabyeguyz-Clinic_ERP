package com.sabyeguyz.clinicv2.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseSaleStaffShareRepository extends JpaRepository<CourseSaleStaffShare, CourseSaleStaffShareId> {
    List<CourseSaleStaffShare> findByCourseId(Long courseId);
}
