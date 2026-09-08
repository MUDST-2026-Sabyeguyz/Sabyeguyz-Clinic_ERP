package com.sabyeguyz.clinic.sale;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    /** ล็อกใบเสร็จก่อนยกเลิก เพื่อกันสอง request ยกเลิกใบเดียวกันแล้วคืนเงินซ้ำ */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select s from Sale s
              join fetch s.patient
              join fetch s.branch
            where s.patient.id = :patientId
            order by s.soldAt desc, s.id desc
            """)
    List<Sale> findByPatient(@Param("patientId") Long patientId);

    Optional<Sale> findByReceiptNo(String receiptNo);
}
