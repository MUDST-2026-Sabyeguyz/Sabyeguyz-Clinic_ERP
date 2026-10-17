package com.sabyeguyz.clinic.report;

import com.sabyeguyz.clinic.sale.SaleItemType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RevenueReportResponse(
        LocalDate from,
        LocalDate to,
        String branchCode,
        BigDecimal totalRevenue,
        List<CategoryRevenue> byCategory,
        List<BranchRevenue> byBranch,
        List<DailyRevenue> daily,
        List<DailyCategoryRevenue> dailyByCategory
) {
    public record CategoryRevenue(SaleItemType type, BigDecimal amount, BigDecimal share) {}

    public record BranchRevenue(String branchCode, String branchName, BigDecimal amount) {}

    public record DailyRevenue(LocalDate date, BigDecimal amount) {}

    public record DailyCategoryRevenue(LocalDate date, SaleItemType type, BigDecimal amount) {}
}
