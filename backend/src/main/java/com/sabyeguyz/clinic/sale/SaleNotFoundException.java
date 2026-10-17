package com.sabyeguyz.clinic.sale;

public class SaleNotFoundException extends RuntimeException {
    public SaleNotFoundException(long id) {
        super("ไม่พบใบเสร็จ id " + id);
    }
}
