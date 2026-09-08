package com.sabyeguyz.clinic.report;

import com.sabyeguyz.clinic.sale.SaleItemType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Revenue Report ตาม Workflow Brief V1 §8 — รายได้รวม รายได้คอร์ส รายได้รายครั้ง
 * กรองตามช่วงเวลาและสาขาได้
 *
 * @param totalRevenue เงินที่รับจริงหักคืนเงิน คำนวณจากรายการชำระเงิน
 * @param byCategory แยกตามชนิดสินค้า นับเฉพาะใบที่ยังไม่ถูกยกเลิก ผลรวมจึงเท่ากับ totalRevenue
 */
public record RevenueReportResponse(
        LocalDate from,
        LocalDate to,
        String branchCode,
        BigDecimal totalRevenue,
        List<CategoryRevenue> byCategory,
        List<BranchRevenue> byBranch,
        List<DailyRevenue> daily
) {
    public record CategoryRevenue(SaleItemType type, BigDecimal amount, BigDecimal share) {}

    public record BranchRevenue(String branchCode, String branchName, BigDecimal amount) {}

    /** ยอดรายวันตามวันที่ของคลินิก (Asia/Bangkok) ไม่ใช่วันที่แบบ UTC */
    public record DailyRevenue(LocalDate date, BigDecimal amount) {}
}
