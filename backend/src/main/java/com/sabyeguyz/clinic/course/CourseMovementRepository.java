package com.sabyeguyz.clinic.course;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseMovementRepository extends JpaRepository<CourseMovement, Long> {

    /** ยอดคงเหลือคือผลรวมของ movement ทั้งหมด ไม่ได้เก็บเป็นคอลัมน์ */
    @Query("select coalesce(sum(m.quantity), 0) from CourseMovement m where m.coursePurchase.id = :purchaseId")
    int balanceOf(@Param("purchaseId") Long purchaseId);

    /**
     * จำนวนครั้งที่ใช้ไปจริง = USAGE ทั้งหมด หักด้วย REVERSAL ที่กลับรายการ USAGE
     * ไม่นับ REVERSAL ของการยกเลิกทั้งใบ เพราะนั่นคือการคืนสิทธิ์ ไม่ใช่การใช้สิทธิ์
     */
    // ต้อง left join ให้ชัด ถ้าเขียน m.reversesMovement.type ตรงๆ JPQL จะทำ inner join
    // แล้วแถว USAGE ที่ reverses_movement_id เป็น NULL จะหายไปทั้งหมด
    @Query("""
            select coalesce(sum(-m.quantity), 0) from CourseMovement m
              left join m.reversesMovement reversed
            where m.coursePurchase.id = :purchaseId
              and (m.type = com.sabyeguyz.clinic.course.CourseMovementType.USAGE
                   or (m.type = com.sabyeguyz.clinic.course.CourseMovementType.REVERSAL
                       and reversed.type = com.sabyeguyz.clinic.course.CourseMovementType.USAGE))
            """)
    int usedSessionsOf(@Param("purchaseId") Long purchaseId);

    List<CourseMovement> findByCoursePurchaseIdOrderByIdAsc(Long purchaseId);

    boolean existsByReversesMovementId(Long movementId);
}
