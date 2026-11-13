package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.catalog.CourseRepository;

/**
 * The monthly course sales that decide a seller's Commission Tier (Data Dictionary: Monthly_commission_closing
 * .monthly_course_sales): the commissionable amount of the course purchases still in effect that the seller made in
 * the sale month (voided and cancelled ones are left out), less the Completed course refunds against those sales, each
 * refund item once. Visits, treatment fees and pay adjustments never count, and the result is never below zero.
 */
@Service
public class MonthlyTierSalesService {
    private final CourseRepository courses;

    public MonthlyTierSalesService(CourseRepository courses) {
        this.courses = courses;
    }

    public record TierSales(
            BigDecimal sales,
            BigDecimal refunds,
            BigDecimal net) {}

    @Transactional(readOnly = true)
    public TierSales of(
            Long staffId,
            String saleMonth) {
        BigDecimal sales = nz(courses.sumCreditedSalesByStaffAndSaleMonth(staffId, saleMonth));
        BigDecimal refunds = nz(courses.sumCompletedRefundsByStaffAndSaleMonth(staffId, saleMonth));

        return new TierSales(
                sales,
                refunds,
                sales.subtract(refunds).max(BigDecimal.ZERO));
    }

    /** The tier base so far, for the sale month. */
    @Transactional(readOnly = true)
    public BigDecimal netSales(
            Long staffId,
            String saleMonth) {
        return of(staffId, saleMonth).net();
    }

    /** Calculates the tier base for one seller, branch and sale month. */
    @Transactional(readOnly = true)
    public TierSales of(
            Long staffId,
            String saleMonth,
            Long branchId) {
        if (branchId == null) {
            throw new IllegalArgumentException("ต้องระบุสาขาสำหรับคำนวณยอดขาย");
        }

        BigDecimal sales = nz(
                courses.sumCreditedSalesByStaffAndSaleMonthAndBranchId(
                        staffId, saleMonth, branchId));

        BigDecimal refunds = nz(
                courses.sumCompletedRefundsByStaffAndSaleMonthAndBranchId(
                        staffId, saleMonth, branchId));

        return new TierSales(
                sales,
                refunds,
                sales.subtract(refunds).max(BigDecimal.ZERO));
    }

    /** Returns the net tier base for the selected branch. */
    @Transactional(readOnly = true)
    public BigDecimal netSales(
            Long staffId,
            String saleMonth,
            Long branchId) {
        return of(staffId, saleMonth, branchId).net();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
