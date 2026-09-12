package com.sabyeguyz.clinic.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    List<Course> findByActiveTrueOrderByName();

    List<Course> findAllByOrderByName();

    Optional<Course> findByCode(String code);
}
