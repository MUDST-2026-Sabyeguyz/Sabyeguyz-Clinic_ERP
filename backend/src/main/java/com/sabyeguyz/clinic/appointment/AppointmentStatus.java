package com.sabyeguyz.clinic.appointment;

import java.util.EnumSet;
import java.util.Set;

/**
 * สถานะนัดหมายทั้ง 7 สถานะตาม Clinic ERP Complete Workflow Brief V1 หัวข้อ 4
 * (Appointment &amp; Patient Visit Workflow)
 */
public enum AppointmentStatus {
    /** นัดหมายแล้ว */
    CONFIRMED,
    /** มาถึงคลินิก */
    ARRIVED,
    /** เข้ารับบริการ */
    IN_SERVICE,
    /** เสร็จสิ้น */
    COMPLETED,
    /** ยกเลิก */
    CANCELLED,
    /** เลื่อนนัด */
    RESCHEDULED,
    /** ไม่มาตามนัด */
    NO_SHOW;

    /**
     * สถานะที่ปล่อยช่วงเวลาของนักกายภาพบำบัดคืน จึงไม่นับเป็นการจองซ้ำ
     * ต้องตรงกับเงื่อนไข WHERE ของ constraint excl_appointments_therapist_overlap
     */
    public static Set<AppointmentStatus> slotReleasing() {
        return EnumSet.of(CANCELLED, NO_SHOW);
    }
}
