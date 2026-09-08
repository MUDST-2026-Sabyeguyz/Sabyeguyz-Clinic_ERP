package com.sabyeguyz.clinic.course;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CoursePurchaseRepository extends JpaRepository<CoursePurchase, Long> {

    /**
     * ล็อกใบซื้อไว้ก่อนอ่านยอดคงเหลือ เพื่อไม่ให้สอง request ตัดสิทธิ์ครั้งสุดท้ายพร้อมกัน
     * แล้วยอดติดลบ
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CoursePurchase p where p.id = :id")
    Optional<CoursePurchase> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select p from CoursePurchase p
              join fetch p.patient
              join fetch p.branch
            where p.patient.id = :patientId
            order by p.purchasedAt desc, p.id desc
            """)
    List<CoursePurchase> findByPatient(@Param("patientId") Long patientId);
}
