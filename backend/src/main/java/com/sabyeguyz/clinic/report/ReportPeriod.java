package com.sabyeguyz.clinic.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * ช่วงวันที่ของรายงาน นับตามเวลาคลินิก ไม่ใช่ UTC
 *
 * <p>เรื่องนี้สำคัญกว่าที่คิด ใบเสร็จที่ออกตอน 22:00 ของวันที่ 8 ตามเวลาไทย
 * ถูกเก็บเป็น 15:00Z ของวันที่ 8 ก็จริง แต่ใบที่ออกตอน 07:00 ของวันที่ 9
 * คือ 00:00Z ของวันที่ 9 ถ้าจัดกลุ่มด้วยฟังก์ชัน date() ของฐานข้อมูลตรงๆ
 * ยอดขายช่วงหัวค่ำจะไปโผล่ผิดวัน จึงแปลงเป็นวันที่ในโซนคลินิกเสมอ</p>
 *
 * @param from วันแรกของช่วง นับรวม
 * @param to วันสุดท้ายของช่วง นับรวม
 */
public record ReportPeriod(LocalDate from, LocalDate to) {
    public static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Bangkok");
    private static final int MAX_DAYS = 732;

    public ReportPeriod {
        if (from == null || to == null) {
            throw new IllegalArgumentException("ต้องระบุช่วงวันที่ทั้ง from และ to");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("วันที่สิ้นสุดต้องไม่มาก่อนวันที่เริ่มต้น");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new IllegalArgumentException("ช่วงเวลาของรายงานต้องไม่เกิน 2 ปี");
        }
    }

    /** ต้นช่วง นับรวม */
    public Instant startInstant() {
        return from.atStartOfDay(CLINIC_ZONE).toInstant();
    }

    /** ท้ายช่วง ไม่นับรวม จึงบวกไปหนึ่งวันเพื่อให้ครอบคลุมทั้งวันสุดท้าย */
    public Instant endInstantExclusive() {
        return to.plusDays(1).atStartOfDay(CLINIC_ZONE).toInstant();
    }

    public LocalDate clinicDateOf(Instant moment) {
        return LocalDate.ofInstant(moment, CLINIC_ZONE);
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
