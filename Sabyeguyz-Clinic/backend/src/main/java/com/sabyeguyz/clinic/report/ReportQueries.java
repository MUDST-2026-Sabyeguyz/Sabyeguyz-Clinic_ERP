package com.sabyeguyz.clinic.report;

import com.sabyeguyz.clinic.appointment.AppointmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Aggregate query ทั้งหมดของรายงาน ทุกตัวรับช่วงเวลาและสาขาแบบเดียวกัน
 * ส่ง branchId เป็น null เมื่อไม่กรองสาขา
 */
public interface ReportQueries extends Repository<com.sabyeguyz.clinic.sale.Sale, Long> {

    /**
     * จำนวนผู้ป่วยที่มาจริงในช่วงเวลา แยกตามเพศ
     * ผู้ป่วยคนหนึ่งมีเพศเดียว จึงรวมทุกกลุ่มแล้วได้จำนวนผู้ป่วยที่ไม่ซ้ำพอดี
     */
    @Query("""
            select new com.sabyeguyz.clinic.report.ReportRows$GenderCount(
                p.gender, count(distinct p.id))
            from Appointment a
              join a.patient p
            where a.startAt >= :from and a.startAt < :to
              and a.status in :attended
              and (:branchId is null or a.branch.id = :branchId)
            group by p.gender
            """)
    List<ReportRows.GenderCount> uniquePatientsByGender(@Param("from") Instant from,
                                                        @Param("to") Instant to,
                                                        @Param("attended") Collection<AppointmentStatus> attended,
                                                        @Param("branchId") Long branchId);

    @Query("""
            select count(a) from Appointment a
            where a.startAt >= :from and a.startAt < :to
              and a.status in :attended
              and (:branchId is null or a.branch.id = :branchId)
            """)
    long countVisits(@Param("from") Instant from,
                     @Param("to") Instant to,
                     @Param("attended") Collection<AppointmentStatus> attended,
                     @Param("branchId") Long branchId);

    @Query("""
            select new com.sabyeguyz.clinic.report.ReportRows$StatusCount(a.status, count(a))
            from Appointment a
            where a.startAt >= :from and a.startAt < :to
              and (:branchId is null or a.branch.id = :branchId)
            group by a.status
            """)
    List<ReportRows.StatusCount> appointmentsByStatus(@Param("from") Instant from,
                                                      @Param("to") Instant to,
                                                      @Param("branchId") Long branchId);

    /**
     * จำนวนนัดต่อนักกายภาพ ส่ง statuses มาเพื่อนับเฉพาะบางสถานะ หรือส่งครบทุกค่าเพื่อนับทั้งหมด
     */
    @Query("""
            select new com.sabyeguyz.clinic.report.ReportRows$TherapistCount(
                t.id, t.code, t.fullName, count(a))
            from Appointment a
              join a.therapist t
            where a.startAt >= :from and a.startAt < :to
              and a.status in :statuses
              and (:branchId is null or a.branch.id = :branchId)
            group by t.id, t.code, t.fullName
            """)
    List<ReportRows.TherapistCount> appointmentsByTherapist(@Param("from") Instant from,
                                                            @Param("to") Instant to,
                                                            @Param("statuses") Collection<AppointmentStatus> statuses,
                                                            @Param("branchId") Long branchId);

    /** เวลาและสถานะของทุกนัด เอาไปจัดกลุ่มรายวันฝั่ง Java ด้วยเหตุผลเดียวกับ revenuePerSale */
    @Query("""
            select new com.sabyeguyz.clinic.report.ReportRows$AppointmentMoment(a.startAt, a.status)
            from Appointment a
            where a.startAt >= :from and a.startAt < :to
              and (:branchId is null or a.branch.id = :branchId)
            """)
    List<ReportRows.AppointmentMoment> appointmentMoments(@Param("from") Instant from,
                                                          @Param("to") Instant to,
                                                          @Param("branchId") Long branchId);

    /**
     * รายได้จริง = ผลรวมของรายการรับเงินหักคืนเงิน ไม่ใช่ sales.total
     * ใบที่ถูกยกเลิกจะมีแถวคืนเงินหักล้างจนเหลือศูนย์เอง ไม่ต้องกรองสถานะ
     */
    @Query("""
            select coalesce(sum(sp.amount), 0) from SalePayment sp
              join sp.sale s
            where s.soldAt >= :from and s.soldAt < :to
              and (:branchId is null or s.branch.id = :branchId)
            """)
    BigDecimal totalRevenue(@Param("from") Instant from,
                            @Param("to") Instant to,
                            @Param("branchId") Long branchId);

    /** แยกรายได้ตามชนิดสินค้า นับเฉพาะใบที่ยังไม่ถูกยกเลิก */
    @Query("""
            select new com.sabyeguyz.clinic.report.ReportRows$CategoryAmount(
                si.itemType, coalesce(sum(si.lineTotal), 0))
            from SaleItem si
              join si.sale s
            where s.soldAt >= :from and s.soldAt < :to
              and s.status = com.sabyeguyz.clinic.sale.SaleStatus.PAID
              and (:branchId is null or s.branch.id = :branchId)
            group by si.itemType
            """)
    List<ReportRows.CategoryAmount> revenueByCategory(@Param("from") Instant from,
                                                      @Param("to") Instant to,
                                                      @Param("branchId") Long branchId);

    @Query("""
            select new com.sabyeguyz.clinic.report.ReportRows$BranchAmount(
                b.code, b.name, coalesce(sum(sp.amount), 0))
            from SalePayment sp
              join sp.sale s
              join s.branch b
            where s.soldAt >= :from and s.soldAt < :to
              and (:branchId is null or b.id = :branchId)
            group by b.code, b.name
            order by b.code
            """)
    List<ReportRows.BranchAmount> revenueByBranch(@Param("from") Instant from,
                                                  @Param("to") Instant to,
                                                  @Param("branchId") Long branchId);

    /**
     * ยอดของแต่ละใบเสร็จพร้อมเวลาที่ขาย เอาไปจัดกลุ่มรายวันต่อฝั่ง Java
     * ไม่ใช้ date() ของฐานข้อมูล เพราะมันตัดวันตาม UTC ยอดหัวค่ำจะเพี้ยนไปวันถัดไป
     */
    @Query("""
            select new com.sabyeguyz.clinic.report.ReportRows$SaleAmount(
                s.soldAt, coalesce(sum(sp.amount), 0))
            from SalePayment sp
              join sp.sale s
            where s.soldAt >= :from and s.soldAt < :to
              and (:branchId is null or s.branch.id = :branchId)
            group by s.id, s.soldAt
            """)
    List<ReportRows.SaleAmount> revenuePerSale(@Param("from") Instant from,
                                               @Param("to") Instant to,
                                               @Param("branchId") Long branchId);

    @Query("select count(s) from Sale s where s.soldAt >= :from and s.soldAt < :to "
            + "and s.status = :status and (:branchId is null or s.branch.id = :branchId)")
    long countReceipts(@Param("from") Instant from,
                       @Param("to") Instant to,
                       @Param("status") com.sabyeguyz.clinic.sale.SaleStatus status,
                       @Param("branchId") Long branchId);

    /**
     * ยอดขายรายบุคคล
     * ต้อง left join ให้ชัด ถ้าเขียน s.salesperson.id ตรงๆ JPQL จะทำ inner join
     * แล้วใบเสร็จที่ไม่ได้ระบุผู้ขายจะหายไปทั้งหมด ยอดรวมรายบุคคลจะไม่เท่ายอดสาขา
     */
    @Query("""
            select new com.sabyeguyz.clinic.report.ReportRows$StaffSaleRow(
                sp.id, sp.employeeCode, sp.fullName, count(s), coalesce(sum(s.total), 0))
            from Sale s
              left join s.salesperson sp
            where s.soldAt >= :from and s.soldAt < :to
              and s.status = com.sabyeguyz.clinic.sale.SaleStatus.PAID
              and (:branchId is null or s.branch.id = :branchId)
            group by sp.id, sp.employeeCode, sp.fullName
            """)
    List<ReportRows.StaffSaleRow> salesByStaff(@Param("from") Instant from,
                                               @Param("to") Instant to,
                                               @Param("branchId") Long branchId);

    @Query("""
            select new com.sabyeguyz.clinic.report.ReportRows$PurchaseRow(
                cp.id, p.id, p.hn, p.fullName, b.code,
                cp.courseCode, cp.courseName, cp.sessions, cp.bonusSessions, cp.price,
                cp.purchasedAt, cp.expiresAt, cp.status)
            from CoursePurchase cp
              join cp.patient p
              join cp.branch b
            where cp.purchasedAt >= :from and cp.purchasedAt < :to
              and (:branchId is null or b.id = :branchId)
              and (:patientId is null or p.id = :patientId)
            order by cp.purchasedAt desc, cp.id desc
            """)
    List<ReportRows.PurchaseRow> coursePurchases(@Param("from") Instant from,
                                                 @Param("to") Instant to,
                                                 @Param("branchId") Long branchId,
                                                 @Param("patientId") Long patientId);

    /** ยอดคงเหลือของหลายใบพร้อมกัน เพื่อไม่ให้เกิด query ต่อหนึ่งใบ */
    @Query("""
            select new com.sabyeguyz.clinic.report.ReportRows$PurchaseTotal(
                m.coursePurchase.id, coalesce(sum(m.quantity), 0))
            from CourseMovement m
            where m.coursePurchase.id in :purchaseIds
            group by m.coursePurchase.id
            """)
    List<ReportRows.PurchaseTotal> balancesOf(@Param("purchaseIds") Collection<Long> purchaseIds);

    /** จำนวนครั้งที่ใช้ไปจริงของหลายใบพร้อมกัน กติกาเดียวกับ CourseMovementRepository.usedSessionsOf */
    @Query("""
            select new com.sabyeguyz.clinic.report.ReportRows$PurchaseTotal(
                m.coursePurchase.id, coalesce(sum(-m.quantity), 0))
            from CourseMovement m
              left join m.reversesMovement reversed
            where m.coursePurchase.id in :purchaseIds
              and (m.type = com.sabyeguyz.clinic.course.CourseMovementType.USAGE
                   or (m.type = com.sabyeguyz.clinic.course.CourseMovementType.REVERSAL
                       and reversed.type = com.sabyeguyz.clinic.course.CourseMovementType.USAGE))
            group by m.coursePurchase.id
            """)
    List<ReportRows.PurchaseTotal> usedSessionsOf(@Param("purchaseIds") Collection<Long> purchaseIds);
}
