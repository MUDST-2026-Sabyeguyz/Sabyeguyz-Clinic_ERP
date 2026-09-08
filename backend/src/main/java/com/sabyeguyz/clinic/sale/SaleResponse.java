package com.sabyeguyz.clinic.sale;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleResponse(
        Long id,
        String receiptNo,
        PatientSummary patient,
        String branchCode,
        SalespersonSummary salesperson,
        /** ยอดที่ต้องชำระ คำนวณจากราคาใน master ตอนขาย */
        BigDecimal total,
        /** เงินที่รับจริงหลังหักการคืนเงินแล้ว ใบที่ถูกยกเลิกจะเป็น 0 */
        BigDecimal paid,
        SaleStatus status,
        String note,
        Instant soldAt,
        Instant voidedAt,
        String voidReason,
        List<SaleItemResponse> items,
        List<SalePaymentResponse> payments
) {
    public record PatientSummary(Long id, String hn, String fullName) {}

    public record SalespersonSummary(Long id, String employeeCode, String fullName) {}

    public record SaleItemResponse(
            Long id,
            SaleItemType type,
            String description,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            /** ใบซื้อคอร์สที่บรรทัดนี้สร้างขึ้น เป็น null สำหรับรายการที่ไม่ใช่คอร์ส */
            Long coursePurchaseId
    ) {
        static SaleItemResponse from(SaleItem item) {
            return new SaleItemResponse(item.getId(), item.getItemType(), item.getDescription(),
                    item.getUnitPrice(), item.getQuantity(), item.getLineTotal(),
                    item.getCoursePurchase() == null ? null : item.getCoursePurchase().getId());
        }
    }

    public record SalePaymentResponse(
            Long id,
            SalePaymentType type,
            String paymentMethodCode,
            BigDecimal amount,
            String reason,
            Long reversesPaymentId,
            Instant createdAt
    ) {
        static SalePaymentResponse from(SalePayment payment) {
            return new SalePaymentResponse(payment.getId(), payment.getType(), payment.getPaymentMethodCode(),
                    payment.getAmount(), payment.getReason(),
                    payment.getReversesPayment() == null ? null : payment.getReversesPayment().getId(),
                    payment.getCreatedAt());
        }
    }

    public static SaleResponse from(Sale sale, BigDecimal paid, List<SaleItem> items, List<SalePayment> payments) {
        return new SaleResponse(
                sale.getId(),
                sale.getReceiptNo(),
                new PatientSummary(sale.getPatient().getId(), sale.getPatient().getHn(),
                        sale.getPatient().getFullName()),
                sale.getBranch().getCode(),
                sale.getSalesperson() == null ? null : new SalespersonSummary(sale.getSalesperson().getId(),
                        sale.getSalesperson().getEmployeeCode(), sale.getSalesperson().getFullName()),
                sale.getTotal(),
                paid,
                sale.getStatus(),
                sale.getNote(),
                sale.getSoldAt(),
                sale.getVoidedAt(),
                sale.getVoidReason(),
                items.stream().map(SaleItemResponse::from).toList(),
                payments.stream().map(SalePaymentResponse::from).toList());
    }
}
