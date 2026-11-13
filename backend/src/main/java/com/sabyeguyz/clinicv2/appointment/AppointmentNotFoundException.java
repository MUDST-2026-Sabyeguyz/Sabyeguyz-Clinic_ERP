package com.sabyeguyz.clinicv2.appointment;

public class AppointmentNotFoundException extends RuntimeException {
    public AppointmentNotFoundException() {
        super("ไม่พบนัดหมาย");
    }
}
