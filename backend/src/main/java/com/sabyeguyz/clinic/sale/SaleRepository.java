package com.sabyeguyz.clinic.sale;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** ลิสต์ใบเสร็จแบบแบ่งหน้า ส่ง null เพื่อไม่กรองเงื่อนไขนั้น */
    @Query(value = """
            select s from Sale s
              join fetch s.patient
              join fetch s.branch
            where (:patientId is null or s.patient.id = :patientId)
              and (:branchId is null or s.branch.id = :branchId)
            """,
            countQuery = """
            select count(s) from Sale s
            where (:patientId is null or s.patient.id = :patientId)
              and (:branchId is null or s.branch.id = :branchId)
            """)
    Page<Sale> search(@Param("patientId") Long patientId,
                      @Param("branchId") Long branchId,
                      Pageable pageable);

    Optional<Sale> findByReceiptNo(String receiptNo);

    @Query("select s from Sale s join fetch s.branch left join fetch s.salesperson where s.status = com.sabyeguyz.clinic.sale.SaleStatus.PAID and s.soldAt >= :from and s.soldAt < :to order by s.soldAt")
    List<Sale> findPaidForCommission(@Param("from") Instant from, @Param("to") Instant to);
}
