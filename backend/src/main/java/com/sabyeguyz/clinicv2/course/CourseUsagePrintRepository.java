package com.sabyeguyz.clinicv2.course;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseUsagePrintRepository extends JpaRepository<CourseUsagePrint, Long> {
    long countByVisitUsageId(Long visitUsageId);

    List<CourseUsagePrint> findByCourseIdOrderByIssuedAtDescDocumentVersionDesc(Long courseId);
}
