package com.sabyeguyz.clinicv2.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseVisitUsageRepository extends JpaRepository<CourseVisitUsage, Long> {
    boolean existsByCourseId(Long courseId);

    List<CourseVisitUsage> findByCourseIdOrderByIdDesc(Long courseId);

    /** Sessions treated in a date range, for the treatment report. */
    default List<CourseVisitUsage> findCompletedBetween(java.time.LocalDate from, java.time.LocalDate to) {
        return findByStatusBetween(com.sabyeguyz.clinicv2.enums.UsageStatus.Completed, from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());
    }

    @org.springframework.data.jpa.repository.Query("select u from CourseVisitUsage u where u.usageStatus = :status "
            + "and u.visitDate >= :from and u.visitDate < :to order by u.visitDate desc, u.id desc")
    List<CourseVisitUsage> findByStatusBetween(
            @org.springframework.data.repository.query.Param("status") com.sabyeguyz.clinicv2.enums.UsageStatus status,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);
}
