package com.sabyeguyz.clinic.sale;

/** สถานะของใบเสร็จไม่ยอมให้ทำรายการที่ขอมา เช่น ยกเลิกใบที่ยกเลิกไปแล้ว */
public class SaleException extends RuntimeException {
    public SaleException(String message) {
        super(message);
    }
}
