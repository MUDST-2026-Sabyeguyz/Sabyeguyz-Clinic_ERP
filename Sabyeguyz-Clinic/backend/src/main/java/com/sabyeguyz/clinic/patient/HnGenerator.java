package com.sabyeguyz.clinic.patient;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class HnGenerator {
    private final EntityManager entityManager;

    public HnGenerator(EntityManager entityManager) { this.entityManager = entityManager; }

    public String next(Long branchId, String numericBranchCode, LocalDate registrationDate) {
        if (numericBranchCode == null || !numericBranchCode.matches("\\d{2}")) {
            throw new IllegalArgumentException("ยังไม่มี Numeric Branch Code สำหรับสาขานี้");
        }
        int year = registrationDate.getYear();
        int month = registrationDate.getMonthValue();
        entityManager.createNativeQuery("""
                MERGE INTO hn_counters AS target
                USING (VALUES (:branchId, :year, :month, 0))
                    AS source(branch_id, registration_year, registration_month, last_number)
                ON target.branch_id = source.branch_id
                    AND target.registration_year = source.registration_year
                    AND target.registration_month = source.registration_month
                WHEN NOT MATCHED THEN
                    INSERT (branch_id, registration_year, registration_month, last_number)
                    VALUES (source.branch_id, source.registration_year, source.registration_month, source.last_number)
                """).setParameter("branchId", branchId).setParameter("year", year)
                .setParameter("month", month).executeUpdate();
        Number current = (Number) entityManager.createNativeQuery("""
                SELECT last_number FROM hn_counters
                WHERE branch_id = :branchId AND registration_year = :year AND registration_month = :month
                FOR UPDATE
                """).setParameter("branchId", branchId).setParameter("year", year)
                .setParameter("month", month).getSingleResult();
        long nextValue = current.longValue() + 1;
        if (nextValue > 9999) throw new IllegalStateException("HN running number เต็มสำหรับสาขาและเดือนนี้");
        entityManager.createNativeQuery("""
                UPDATE hn_counters SET last_number = :next
                WHERE branch_id = :branchId AND registration_year = :year AND registration_month = :month
                """).setParameter("next", nextValue).setParameter("branchId", branchId).setParameter("year", year)
                .setParameter("month", month).executeUpdate();
        return format(registrationDate, numericBranchCode, nextValue);
    }

    static String format(LocalDate registrationDate, String numericBranchCode, long runningNumber) {
        return "%02d%s%02d%04d".formatted(registrationDate.getYear() % 100,
                numericBranchCode, registrationDate.getMonthValue(), runningNumber);
    }
}
