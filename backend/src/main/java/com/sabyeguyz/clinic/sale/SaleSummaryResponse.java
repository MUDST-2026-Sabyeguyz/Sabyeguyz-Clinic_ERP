package com.sabyeguyz.clinic.sale;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ใบเสร็จแบบย่อสำหรับหน้าลิสต์ ไม่มีรายการสินค้าและรายการชำระเงิน
 * ถ้าต้องการรายละเอียดทั้งหมดให้เรียก {@code GET /api/receipts/{id}}
 */
public record SaleSummaryResponse(
        Long id,
        String receiptNo,
        SaleResponse.PatientSummary patient,
        String branchCode,
        SaleResponse.SalespersonSummary salesperson,
        BigDecimal total,
        BigDecimal paid,
        SaleStatus status,
        Instant soldAt,
        Instant voidedAt
) {
    static SaleSummaryResponse from(Sale sale, BigDecimal paid) {
        return new SaleSummaryResponse(
                sale.getId(),
                sale.getReceiptNo(),
                new SaleResponse.PatientSummary(sale.getPatient().getId(), sale.getPatient().getHn(),
                        sale.getPatient().getFullName()),
                sale.getBranch().getCode(),
                sale.getSalesperson() == null ? null : new SaleResponse.SalespersonSummary(
                        sale.getSalesperson().getId(), sale.getSalesperson().getEmployeeCode(),
                        sale.getSalesperson().getFullName()),
                sale.getTotal(),
                paid,
                sale.getStatus(),
                sale.getSoldAt(),
                sale.getVoidedAt());
    }
}
