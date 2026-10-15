package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.enums.ServiceType;
import com.sabyeguyz.clinicv2.sale.Discount;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.transaction.annotation.Transactional;

/**
 * Estimates the commission a course sale would earn before it is paid, so the
 * sale screen can show it. Mirrors the provisional-rate part of
 * {@code CourseService.sell}: the final rate is still locked at Monthly
 * Closing, so this is an estimate, never a stored value.
 */
@org.springframework.stereotype.Service
public class CommissionPreviewService {
    private static final DateTimeFormatter SALE_MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CourseRepository courses;
    private final CommissionSchemeResolver schemeResolver;
    private final CommissionTierResolver tierResolver;
    private final MonthlyTierSalesService tierSales;

    public CommissionPreviewService(CourseRepository courses, CommissionSchemeResolver schemeResolver,
                                     CommissionTierResolver tierResolver, MonthlyTierSalesService tierSales) {
        this.tierSales = tierSales;
        this.courses = courses;
        this.schemeResolver = schemeResolver;
        this.tierResolver = tierResolver;
    }

    public record Preview(BigDecimal netPrice, BigDecimal commissionRate, BigDecimal commissionPool,
                           BigDecimal commissionPerVisit, int totalVisits, int schemeVersion,
                           BigDecimal monthlySalesToDate) {}

    @Transactional(readOnly = true)
    public Preview previewCourse(Service courseTemplate, Staff seller, BigDecimal priceOverride, Discount discount,
                                  LocalDate saleDate) {
        if (courseTemplate == null || courseTemplate.getServiceType() != ServiceType.Course) {
            throw new IllegalArgumentException("ต้องระบุคอร์ส");
        }
        if (seller == null) {
            throw new IllegalArgumentException("ต้องระบุผู้ขาย");
        }
        Integer totalVisits = courseTemplate.getDefaultTotalVisits();
        if (totalVisits == null || totalVisits < 1) {
            throw new IllegalArgumentException("คอร์สนี้ไม่ได้ตั้งค่าจำนวนครั้ง");
        }
        LocalDate date = saleDate == null ? LocalDate.now() : saleDate;
        BigDecimal gross = priceOverride != null ? priceOverride : courseTemplate.getServicePrice();
        BigDecimal net = (discount == null ? Discount.none() : discount).apply(gross);

        CommissionScheme scheme = schemeResolver.resolveEffective(date);
        BigDecimal monthlySalesToDate = tierSales
                .netSales(seller.getId(), date.format(SALE_MONTH)).add(net);
        BigDecimal rate = tierResolver.resolve(scheme, monthlySalesToDate).getCommissionRate();

        BigDecimal pool = net.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal perVisit = pool.divide(BigDecimal.valueOf(totalVisits), 2, RoundingMode.HALF_UP);
        return new Preview(net, rate, pool, perVisit, totalVisits, scheme.getSchemeVersion(), monthlySalesToDate);
    }
}
