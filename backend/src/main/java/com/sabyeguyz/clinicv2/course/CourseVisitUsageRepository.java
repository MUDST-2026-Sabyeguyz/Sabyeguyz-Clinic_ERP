package com.sabyeguyz.clinicv2.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseVisitUsageRepository extends JpaRepository<CourseVisitUsage, Long> {
    List<CourseVisitUsage> findByCourseIdOrderByIdDesc(Long courseId);
}
