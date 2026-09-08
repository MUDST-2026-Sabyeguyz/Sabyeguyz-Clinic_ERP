package com.sabyeguyz.clinic.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Staff Sales Report ตาม Workflow Brief V1 §8 — ยอดขายทั้งสาขาและรายบุคคล
 *
 * @param unassigned ยอดของใบเสร็จที่ไม่ได้ระบุผู้ขาย เก็บแยกไว้ให้เห็น ไม่ได้ซ่อนทิ้ง
 *                   ผลรวมของ rows บวก unassigned จึงเท่ากับ branchTotal เสมอ
 */
public record StaffSalesReportResponse(
        LocalDate from,
        LocalDate to,
        String branchCode,
        BigDecimal branchTotal,
        long receipts,
        List<Row> rows,
        Row unassigned
) {
    public record Row(
            Long staffId,
            String employeeCode,
            String fullName,
            long receipts,
            BigDecimal amount
    ) {}
}
