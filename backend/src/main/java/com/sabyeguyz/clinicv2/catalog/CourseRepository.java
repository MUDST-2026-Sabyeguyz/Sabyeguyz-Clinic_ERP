package com.sabyeguyz.clinicv2.catalog;

import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCourseNo(String courseNo);

    long countByReceiptId(Long receiptId);

    /** Sum of course_price sold by a seller within a given sale_month (yyyy-mm), for Commission Tier lookup. */
    @Query("select coalesce(sum(c.coursePrice), 0) from Course c "
            + "where c.sellerStaff.id = :sellerStaffId and c.saleMonth = :saleMonth")
    BigDecimal sumCoursePriceBySellerAndSaleMonth(@Param("sellerStaffId") Long sellerStaffId,
                                                   @Param("saleMonth") String saleMonth);
}
