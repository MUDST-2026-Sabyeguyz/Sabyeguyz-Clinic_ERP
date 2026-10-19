package com.sabyeguyz.clinicv2.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseLedgerRepository extends JpaRepository<CourseLedger, Long> {
    /**
     * A patient's own running balance for a Course is the sum of their ledger
     * entries — not Course.remainingVisits, which is the course-wide total
     * shared across every patient on it (Commission_System_Requirement_Dev
     * §16 "Shared Course").
     */
    @Query("select coalesce(sum(l.qtyChange), 0) from CourseLedger l "
            + "where l.course.id = :courseId and l.patient.id = :patientId")
    Long sumQtyChangeByCourseAndPatient(@Param("courseId") Long courseId, @Param("patientId") Long patientId);

    List<CourseLedger> findByCourseIdOrderByIdDesc(Long courseId);

    List<CourseLedger> findByVisitUsageId(Long visitUsageId);

    List<CourseLedger> findByTransferId(Long transferId);

    boolean existsByCourseRefundItemId(Long courseRefundItemId);

    boolean existsByCancellationIdAndCourseId(Long cancellationId, Long courseId);
}
