package com.sabyeguyz.clinic.course;

/** สถานะของคอร์สไม่อนุญาตให้ทำรายการนี้ เช่น หมดอายุ สิทธิ์ไม่พอ หรือกลับรายการซ้ำ */
public class CourseBalanceException extends RuntimeException {
    public CourseBalanceException(String message) {
        super(message);
    }
}
