package com.sabyeguyz.clinicv2.sale;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    Optional<Receipt> findByReceiptNo(String receiptNo);
    Optional<Receipt> findByReceiptNoAndBranchId(String receiptNo, Long branchId);

    /** Highest running number in receipt_no (RE-xxxxxxx), 0 when there is no receipt yet. */
    @Query("select coalesce(max(cast(substring(r.receiptNo, 3) as integer)), 0) from Receipt r")
    int findMaxReceiptSequence();

    @Query(value = "select next_receipt_running_number(:branchId, :month)", nativeQuery = true)
    int nextRunningNumber(@Param("branchId") Long branchId, @Param("month") String month);

    @Query("select r from Receipt r where cast(r.saleDate as date) between :from and :to "
            + "and (:branchId is null or r.branch.id = :branchId)")
    List<Receipt> findBySaleDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                         @Param("branchId") Long branchId);
}
