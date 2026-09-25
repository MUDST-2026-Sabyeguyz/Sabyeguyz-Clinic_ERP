package com.sabyeguyz.clinicv2.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCourseNo(String courseNo);
}
