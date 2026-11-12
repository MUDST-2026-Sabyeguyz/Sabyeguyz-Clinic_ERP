package com.sabyeguyz.clinicv2.course;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseEntitlementRepository extends JpaRepository<CourseEntitlement, CourseEntitlementId> {
    @Query("select e from CourseEntitlement e where e.course.id = :courseId")
    List<CourseEntitlement> findByCourseId(@Param("courseId") Long courseId);
}
