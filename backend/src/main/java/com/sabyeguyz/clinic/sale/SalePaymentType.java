package com.sabyeguyz.clinic.sale;

public enum SalePaymentType {
    /** รับเงินเข้า ยอดเป็นบวก */
    PAYMENT,
    /** คืนเงินออก ยอดเป็นลบ และชี้กลับไปยังรายการรับเงินที่กลับรายการ */
    REFUND
}
