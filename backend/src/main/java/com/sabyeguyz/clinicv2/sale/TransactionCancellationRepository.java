package com.sabyeguyz.clinicv2.sale;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionCancellationRepository extends JpaRepository<TransactionCancellation, Long> {
    java.util.Optional<TransactionCancellation> findFirstByTransactionReceiptIdOrderByCancelledAtDesc(Long receiptId);
}
