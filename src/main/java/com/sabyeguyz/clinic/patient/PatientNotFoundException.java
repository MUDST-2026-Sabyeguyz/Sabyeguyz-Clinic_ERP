package com.sabyeguyz.clinic.patient;

public class PatientNotFoundException extends RuntimeException {
    public PatientNotFoundException(long id) {
        super("ไม่พบผู้ป่วย id " + id);
    }
}
