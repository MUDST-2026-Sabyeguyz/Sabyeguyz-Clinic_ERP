package com.sabyeguyz.clinic.course;

/**
 * ชนิดของรายการเดินบัญชีคอร์สตาม Workflow Brief V1 §5 "Course Ledger"
 * ยังไม่รองรับ TRANSFER_IN/TRANSFER_OUT/ADJUSTMENT — จะเพิ่มพร้อมฟีเจอร์โอนคอร์ส
 */
public enum CourseMovementType {
    /** ซื้อคอร์ส: +(sessions + bonus) */
    PURCHASE,
    /** ใช้สิทธิ์ 1 ครั้ง: -1 */
    USAGE,
    /** กลับรายการเดิม: จำนวนตรงข้ามกับ movement ที่อ้างถึง */
    REVERSAL
}
