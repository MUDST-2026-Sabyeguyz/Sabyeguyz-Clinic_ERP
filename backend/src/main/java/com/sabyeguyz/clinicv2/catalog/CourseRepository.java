package com.sabyeguyz.clinicv2.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCourseNo(String courseNo);

    long countByReceiptId(Long receiptId);

    List<Course> findBySellerStaffIdAndSaleMonth(Long sellerStaffId, String saleMonth);

    /** Sum of course_price sold by a seller within a given sale_month (yyyy-mm), for Commission Tier lookup. */
    @Query("select coalesce(sum(c.coursePrice), 0) from Course c "
            + "where c.sellerStaff.id = :sellerStaffId and c.saleMonth = :saleMonth")
    BigDecimal sumCoursePriceBySellerAndSaleMonth(@Param("sellerStaffId") Long sellerStaffId,
                                                   @Param("saleMonth") String saleMonth);

    /** Every Course a patient can use: as the Receipt's buyer (implicit Owner) or as a Shared Member. */
    @Query("select distinct c from Course c left join CourseSharedMember m on m.course = c "
            + "where c.receipt.patient.id = :patientId or m.patient.id = :patientId")
    List<Course> findByPatientId(@Param("patientId") Long patientId);
}
