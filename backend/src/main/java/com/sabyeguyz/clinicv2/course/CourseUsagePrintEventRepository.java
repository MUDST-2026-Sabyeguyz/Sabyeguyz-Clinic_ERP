package com.sabyeguyz.clinicv2.course;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseUsagePrintEventRepository extends JpaRepository<CourseUsagePrintEvent, Long> {
    List<CourseUsagePrintEvent> findByPrintIdOrderByPrintedAtAsc(Long printId);
}
