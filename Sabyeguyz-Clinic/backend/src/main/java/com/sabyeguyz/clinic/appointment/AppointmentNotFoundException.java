package com.sabyeguyz.clinic.appointment;

public class AppointmentNotFoundException extends RuntimeException {
    public AppointmentNotFoundException(long id) {
        super("ไม่พบการนัดหมาย id " + id);
    }
}