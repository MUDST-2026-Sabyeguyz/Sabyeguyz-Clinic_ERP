package com.sabyeguyz.clinicv2.catalog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCourseNo(String courseNo);

    long countByReceiptId(Long receiptId);

    boolean existsBySaleTransactionId(Long saleTransactionId);

    List<Course> findBySellerStaffIdAndSaleMonth(Long sellerStaffId, String saleMonth);

    /** Sales credited to a staff member in a sale_month (yyyy-mm), for Commission Tier lookup. */
    @Query("select coalesce(sum(s.creditedSalesAmount), 0) from CourseSaleStaffShare s "
            + "where s.staff.id = :staffId and s.course.saleMonth = :saleMonth")
    BigDecimal sumCreditedSalesByStaffAndSaleMonth(@Param("staffId") Long staffId,
                                                    @Param("saleMonth") String saleMonth);

    /** Every Course a patient can use: as the purchaser (implicit Owner) or as a Shared Member. */
    @Query("select distinct c from Course c left join CourseSharedMember m on m.course = c "
            + "where c.purchaserPatient.id = :patientId or m.patient.id = :patientId")
    List<Course> findByPatientId(@Param("patientId") Long patientId);

    @Query("select c from Course c where cast(c.receipt.saleDate as date) between :from and :to")
    List<Course> findByReceiptSaleDateBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select distinct s.staff.id from CourseSaleStaffShare s where s.course.saleMonth = :saleMonth")
    List<Long> findDistinctCreditedStaffIdsBySaleMonth(@Param("saleMonth") String saleMonth);
}
