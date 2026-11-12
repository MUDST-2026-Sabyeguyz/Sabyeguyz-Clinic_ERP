package com.sabyeguyz.clinicv2.patient;

public class PatientNotFoundException extends RuntimeException {
    public PatientNotFoundException() {
        super("ไม่พบผู้ป่วย");
    }
}
