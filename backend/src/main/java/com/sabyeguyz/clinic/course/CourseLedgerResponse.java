package com.sabyeguyz.clinic.course;

import java.util.List;

/** ใบซื้อหนึ่งใบพร้อมประวัติการเปลี่ยนแปลงยอดทั้งหมด */
public record CourseLedgerResponse(
        CoursePurchaseResponse purchase,
        List<CourseMovementResponse> movements,
        List<com.sabyeguyz.clinic.commission.CommissionAllocationLedger> allocations
) {}
