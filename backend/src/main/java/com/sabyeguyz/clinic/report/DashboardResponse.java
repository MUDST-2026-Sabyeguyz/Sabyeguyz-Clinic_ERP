package com.sabyeguyz.clinic.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ตัวเลขหน้า Dashboard ตาม Workflow Brief V1 §8
 *
 * @param uniquePatients ผู้ป่วยที่ไม่ซ้ำซึ่ง "มาถึงคลินิกจริง" ในช่วงนี้ ไม่ใช่ผู้ป่วยที่ลงทะเบียนทั้งหมด
 * @param visits จำนวนครั้งที่มารับบริการ ผู้ป่วยคนเดียวมาสามครั้งนับเป็น 3
 * @param totalRevenue เงินที่รับจริงหักคืนเงินแล้ว
 */
public record DashboardResponse(
        LocalDate from,
        LocalDate to,
        String branchCode,
        long uniquePatients,
        List<GenderBreakdown> patientsByGender,
        long visits,
        BigDecimal totalRevenue,
        BigDecimal courseRevenue,
        BigDecimal singleVisitRevenue,
        BigDecimal assessmentRevenue,
        long receiptsIssued,
        long receiptsVoided
) {
    public record GenderBreakdown(String gender, long patients) {}
}
