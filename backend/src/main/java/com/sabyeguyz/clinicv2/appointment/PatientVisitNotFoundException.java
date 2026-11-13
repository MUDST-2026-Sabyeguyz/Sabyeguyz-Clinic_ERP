package com.sabyeguyz.clinicv2.appointment;

public class PatientVisitNotFoundException extends RuntimeException {
    public PatientVisitNotFoundException() {
        super("ไม่พบข้อมูลการมารับบริการ");
    }
}
