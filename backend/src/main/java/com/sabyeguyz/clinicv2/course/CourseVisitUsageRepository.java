package com.sabyeguyz.clinicv2.course;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseVisitUsageRepository extends JpaRepository<CourseVisitUsage, Long> {
    boolean existsByCourseId(Long courseId);

    List<CourseVisitUsage> findByCourseIdOrderByIdDesc(Long courseId);
    List<CourseVisitUsage> findByVisitIdOrderByIdAsc(Long visitId);

    /** Finds owners with unknown commission in the selling branch. */
    default List<Long> findPendingOwnerStaffIdsForBranch(
            Long branchId,
            java.time.LocalDate from,
            java.time.LocalDate to) {
        return findPendingOwnerStaffIds(
                branchId,
                com.sabyeguyz.clinicv2.enums.UsageStatus.Completed,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());
    }

    @org.springframework.data.jpa.repository.Query(
            "select distinct u.course.sellerStaff.id from CourseVisitUsage u "
                    + "where u.course.branch.id = :branchId "
                    + "and u.usageStatus = :status "
                    + "and u.ownerNetCommission is null "
                    + "and u.visitDate >= :from "
                    + "and u.visitDate < :to")
    List<Long> findPendingOwnerStaffIds(
            @org.springframework.data.repository.query.Param("branchId")
            Long branchId,
            @org.springframework.data.repository.query.Param("status")
            com.sabyeguyz.clinicv2.enums.UsageStatus status,
            @org.springframework.data.repository.query.Param("from")
            java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to")
            java.time.LocalDateTime to);

    /** Counts unknown owner commissions assigned to the selling branch. */
    default long countPendingOwnerNetForBranch(
            Long sellerStaffId,
            Long branchId,
            java.time.LocalDate from,
            java.time.LocalDate to) {
        return countPendingForBranch(
                sellerStaffId,
                branchId,
                com.sabyeguyz.clinicv2.enums.UsageStatus.Completed,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());
    }

    @org.springframework.data.jpa.repository.Query(
            "select count(u) from CourseVisitUsage u "
                    + "where u.course.sellerStaff.id = :staffId "
                    + "and u.course.branch.id = :branchId "
                    + "and u.usageStatus = :status "
                    + "and u.ownerNetCommission is null "
                    + "and u.visitDate >= :from "
                    + "and u.visitDate < :to")
    long countPendingForBranch(
            @org.springframework.data.repository.query.Param("staffId")
            Long staffId,
            @org.springframework.data.repository.query.Param("branchId")
            Long branchId,
            @org.springframework.data.repository.query.Param("status")
            com.sabyeguyz.clinicv2.enums.UsageStatus status,
            @org.springframework.data.repository.query.Param("from")
            java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to")
            java.time.LocalDateTime to);

    /** Completed sessions of the courses a seller sold whose Owner commission is still unknown, treated in the range. */
    default long countPendingOwnerNet(
            Long sellerStaffId,
            java.time.LocalDate from,
            java.time.LocalDate to) {
        return countPending(
                sellerStaffId,
                com.sabyeguyz.clinicv2.enums.UsageStatus.Completed,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());
    }

    @org.springframework.data.jpa.repository.Query("select count(u) from CourseVisitUsage u where u.course.sellerStaff.id = :staffId "
            + "and u.usageStatus = :status and u.ownerNetCommission is null and u.visitDate >= :from and u.visitDate < :to")
    long countPending(
            @org.springframework.data.repository.query.Param("staffId") Long staffId,
            @org.springframework.data.repository.query.Param("status") com.sabyeguyz.clinicv2.enums.UsageStatus status,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);

    /** Sessions treated in a date range, for the treatment report. */
    default List<CourseVisitUsage> findCompletedBetween(
            java.time.LocalDate from,
            java.time.LocalDate to) {
        return findByStatusBetween(
                com.sabyeguyz.clinicv2.enums.UsageStatus.Completed,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());
    }

    @org.springframework.data.jpa.repository.Query("select u from CourseVisitUsage u where u.usageStatus = :status "
            + "and u.visitDate >= :from and u.visitDate < :to order by u.visitDate desc, u.id desc")
    List<CourseVisitUsage> findByStatusBetween(
            @org.springframework.data.repository.query.Param("status") com.sabyeguyz.clinicv2.enums.UsageStatus status,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
            @org.springframework.data.repository.query.Param("to") java.time.LocalDateTime to);
}
