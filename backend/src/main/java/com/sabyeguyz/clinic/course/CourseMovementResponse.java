package com.sabyeguyz.clinic.course;

import java.time.Instant;

public record CourseMovementResponse(
        Long id,
        CourseMovementType type,
        int quantity,
        /** ยอดคงเหลือหลังรายการนี้ ช่วยให้หน้าจอแสดงบัญชีเดินสะพัดได้โดยไม่ต้องคำนวณเอง */
        int balanceAfter,
        String reason,
        Long appointmentId,
        Long reversesMovementId,
        Instant createdAt
) {
    public static CourseMovementResponse from(CourseMovement movement, int balanceAfter) {
        return new CourseMovementResponse(
                movement.getId(),
                movement.getType(),
                movement.getQuantity(),
                balanceAfter,
                movement.getReason(),
                movement.getAppointment() == null ? null : movement.getAppointment().getId(),
                movement.getReversesMovement() == null ? null : movement.getReversesMovement().getId(),
                movement.getCreatedAt());
    }
}
