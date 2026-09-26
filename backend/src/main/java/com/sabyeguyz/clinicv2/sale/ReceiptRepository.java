package com.sabyeguyz.clinicv2.sale;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    Optional<Receipt> findByReceiptNo(String receiptNo);

    @Query("select r from Receipt r where r.saleDate between :from and :to "
            + "and (:branchId is null or r.branch.id = :branchId)")
    List<Receipt> findBySaleDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                         @Param("branchId") Long branchId);
}
