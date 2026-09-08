package com.sabyeguyz.clinic.course;

public class CoursePurchaseNotFoundException extends RuntimeException {
    public CoursePurchaseNotFoundException(long id) {
        super("ไม่พบใบซื้อคอร์ส id " + id);
    }
}
