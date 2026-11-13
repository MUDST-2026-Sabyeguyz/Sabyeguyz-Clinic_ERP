package com.sabyeguyz.clinicv2.commission;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SingleVisitFeeLedgerRepository extends JpaRepository<SingleVisitFeeLedger, Long> {
    /** Finds single-visit fees earned at the selected branch. */
    @org.springframework.data.jpa.repository.Query(
            "select f from SingleVisitFeeLedger f "
                    + "where f.staff.id = :staffId "
                    + "and f.visit.branch.id = :branchId "
                    + "and f.serviceDate between :from and :to "
                    + "order by f.id")
    java.util.List<SingleVisitFeeLedger> findStaffFeesForBranch(
            @org.springframework.data.repository.query.Param("staffId")
            Long staffId,
            @org.springframework.data.repository.query.Param("branchId")
            Long branchId,
            @org.springframework.data.repository.query.Param("from")
            java.time.LocalDate from,
            @org.springframework.data.repository.query.Param("to")
            java.time.LocalDate to);

    /** Finds all single-visit fees for a branch within the date range. */
    @org.springframework.data.jpa.repository.Query(
            "select f from SingleVisitFeeLedger f "
                    + "where f.visit.branch.id = :branchId "
                    + "and f.serviceDate between :from and :to "
                    + "order by f.id")
    java.util.List<SingleVisitFeeLedger> findBranchFeesBetween(
            @org.springframework.data.repository.query.Param("branchId")
            Long branchId,
            @org.springframework.data.repository.query.Param("from")
            java.time.LocalDate from,
            @org.springframework.data.repository.query.Param("to")
            java.time.LocalDate to);

    java.util.List<SingleVisitFeeLedger> findByVisitIdOrderByIdAsc(Long visitId);

    java.util.List<SingleVisitFeeLedger> findByStaffIdAndServiceDateBetweenOrderByIdAsc(
            Long staffId,
            java.time.LocalDate from,
            java.time.LocalDate to);

    java.util.List<SingleVisitFeeLedger> findByServiceDateBetween(
            java.time.LocalDate from,
            java.time.LocalDate to);
}
