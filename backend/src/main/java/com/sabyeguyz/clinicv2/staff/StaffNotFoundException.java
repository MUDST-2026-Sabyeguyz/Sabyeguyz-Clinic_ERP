package com.sabyeguyz.clinicv2.staff;

public class StaffNotFoundException extends RuntimeException {
    public StaffNotFoundException() {
        super("ไม่พบพนักงาน");
    }
}
