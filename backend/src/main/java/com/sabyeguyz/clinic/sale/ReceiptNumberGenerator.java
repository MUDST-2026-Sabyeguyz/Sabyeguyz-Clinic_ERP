package com.sabyeguyz.clinic.sale;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * เลขที่ใบเสร็จ RC + ปี 2 หลัก + รหัสสาขา 2 หลัก + เดือน 2 หลัก + running 4 หลัก
 * เดินแยกตามสาขาและเดือน แล้วรีเซ็ตทุกเดือน เหมือนกติกาของ HN
 */
@Service
public class ReceiptNumberGenerator {
    private final EntityManager entityManager;

    public ReceiptNumberGenerator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public String next(Long branchId, String numericBranchCode, LocalDate issueDate) {
        if (numericBranchCode == null || !numericBranchCode.matches("\\d{2}")) {
            throw new IllegalArgumentException("ยังไม่มี Numeric Branch Code สำหรับสาขานี้");
        }
        int year = issueDate.getYear();
        int month = issueDate.getMonthValue();
        // จองสิทธิ์เดินเลขของสาขานี้ก่อนแตะตารางตัวนับ
        // ถ้าไม่ล็อกตรงนี้ สอง request ที่ออกใบเสร็จ "ใบแรกของเดือน" พร้อมกันจะแย่งกัน INSERT
        // แถวตัวนับแถวเดียวกัน แล้วฝ่ายที่ช้ากว่าจะชน primary key จนออกใบเสร็จไม่ได้
        entityManager.createNativeQuery("SELECT id FROM branches WHERE id = :branchId FOR UPDATE")
                .setParameter("branchId", branchId)
                .getSingleResult();
        entityManager.createNativeQuery("""
                MERGE INTO receipt_counters AS target
                USING (VALUES (:branchId, :year, :month, 0))
                    AS source(branch_id, receipt_year, receipt_month, last_number)
                ON target.branch_id = source.branch_id
                    AND target.receipt_year = source.receipt_year
                    AND target.receipt_month = source.receipt_month
                WHEN NOT MATCHED THEN
                    INSERT (branch_id, receipt_year, receipt_month, last_number)
                    VALUES (source.branch_id, source.receipt_year, source.receipt_month, source.last_number)
                """).setParameter("branchId", branchId).setParameter("year", year)
                .setParameter("month", month).executeUpdate();
        // FOR UPDATE ทำให้สอง request ที่ออกใบเสร็จพร้อมกันเข้าคิวกัน เลขจึงไม่ซ้ำ
        Number current = (Number) entityManager.createNativeQuery("""
                SELECT last_number FROM receipt_counters
                WHERE branch_id = :branchId AND receipt_year = :year AND receipt_month = :month
                FOR UPDATE
                """).setParameter("branchId", branchId).setParameter("year", year)
                .setParameter("month", month).getSingleResult();
        long nextValue = current.longValue() + 1;
        if (nextValue > 9999) {
            throw new IllegalStateException("เลขที่ใบเสร็จเต็มสำหรับสาขาและเดือนนี้");
        }
        entityManager.createNativeQuery("""
                UPDATE receipt_counters SET last_number = :next
                WHERE branch_id = :branchId AND receipt_year = :year AND receipt_month = :month
                """).setParameter("next", nextValue).setParameter("branchId", branchId).setParameter("year", year)
                .setParameter("month", month).executeUpdate();
        return format(issueDate, numericBranchCode, nextValue);
    }

    static String format(LocalDate issueDate, String numericBranchCode, long runningNumber) {
        return "RC%02d%s%02d%04d".formatted(issueDate.getYear() % 100,
                numericBranchCode, issueDate.getMonthValue(), runningNumber);
    }
}
