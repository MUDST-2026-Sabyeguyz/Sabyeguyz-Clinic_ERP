package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The Owner commission rate of each session of a course (Data Dictionary: Course.commission_allocation_per_visit).
 * The pool (course price x locked rate) is spread over the sessions of the course by their position (usage_sequence,
 * all users together, not by how many sessions the Owner treated): every session but the last gets
 * ROUND(pool / total_visits, 2), the last gets what is left of the pool (pool - nominal x (total_visits - 1), for
 * example 53.85 x 12 and 53.80). No session gets more than the budget still left of the pool, and none gets less than
 * zero. The rate is the amount the Owner earns when the Owner treats that session; a substitute treating it earns the
 * Owner nothing and the amount is not moved to another session.
 */
public final class CommissionAllocationCalculator {
    private CommissionAllocationCalculator() {}

    /** The nominal rate of one session. */
    public static BigDecimal nominal(BigDecimal pool, int totalVisits) {
        requirePositive(totalVisits);
        return pool.divide(BigDecimal.valueOf(totalVisits), 2, RoundingMode.HALF_UP);
    }

    /** The rate of the session at {@code position} (1..totalVisits). */
    public static BigDecimal forPosition(BigDecimal pool, int totalVisits, int position) {
        requirePositive(totalVisits);
        if (position < 1 || position > totalVisits) {
            throw new IllegalArgumentException("usage_sequence ต้องอยู่ระหว่าง 1 ถึง " + totalVisits);
        }
        BigDecimal pool2 = pool.setScale(2, RoundingMode.HALF_UP);
        BigDecimal nominal = nominal(pool2, totalVisits);
        BigDecimal before = nominal.multiply(BigDecimal.valueOf(position - 1)).min(pool2);
        BigDecimal left = pool2.subtract(before);
        BigDecimal amount = position == totalVisits ? left : nominal.min(left);
        return amount.max(BigDecimal.ZERO);
    }

    private static void requirePositive(int totalVisits) {
        if (totalVisits < 1) {
            throw new IllegalArgumentException("total_visits ต้องไม่น้อยกว่า 1");
        }
    }
}
