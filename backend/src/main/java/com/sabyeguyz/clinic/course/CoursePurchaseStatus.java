package com.sabyeguyz.clinic.course;

public enum CoursePurchaseStatus {
    /** ใบซื้อปกติ ใช้สิทธิ์ได้ถ้ายังไม่หมดอายุและยังมี remaining */
    ACTIVE,
    /** ใบซื้อถูกยกเลิกทั้งใบ สิทธิ์คงเหลือถูกกลับรายการไปแล้ว */
    CANCELLED
}
