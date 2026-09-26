package com.sabyeguyz.clinicv2.sale;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByTransactionIdOrderByIdAsc(Long transactionId);

    @Query("select coalesce(sum(p.amount), 0) from PaymentTransaction p "
            + "where p.transaction.id = :transactionId and p.paymentStatus = true")
    BigDecimal paidAmountOf(@Param("transactionId") Long transactionId);
}
