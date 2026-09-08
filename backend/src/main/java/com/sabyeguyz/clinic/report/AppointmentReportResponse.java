package com.sabyeguyz.clinic.report;

import com.sabyeguyz.clinic.appointment.AppointmentStatus;
import java.time.LocalDate;
import java.util.List;

/**
 * Appointment Report ตาม docs/context.md §8
 *
 * @param appointments นัดทั้งหมดในช่วง ไม่ว่าสถานะใด
 * @param visits นัดที่ผู้ป่วยมาถึงจริง นิยามเดียวกับ Dashboard
 * @param noShowRate สัดส่วนการไม่มาตามนัด เทียบกับนัดที่ถึงกำหนดแล้ว (ไม่นับที่ยังไม่ถึงเวลาและที่เลื่อน)
 */
public record AppointmentReportResponse(
        LocalDate from,
        LocalDate to,
        String branchCode,
        long appointments,
        long visits,
        java.math.BigDecimal noShowRate,
        List<StatusCount> byStatus,
        List<TherapistLoad> byTherapist,
        List<DailyCount> daily
) {
    public record StatusCount(AppointmentStatus status, long appointments) {}

    public record TherapistLoad(Long therapistId, String code, String fullName,
                                long appointments, long visits) {}

    /** จำนวนนัดรายวันตามวันที่ของคลินิก (Asia/Bangkok) */
    public record DailyCount(LocalDate date, long appointments, long visits) {}
}
