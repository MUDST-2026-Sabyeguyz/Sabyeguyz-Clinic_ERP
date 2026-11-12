package com.sabyeguyz.clinicv2.commission;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks the Commission Scheme effective on a given date; both ends of a scheme's range are inclusive and exactly one
 * scheme must apply (Data Dictionary: Commission_scheme).
 */
@Service
public class CommissionSchemeResolver {
    private final CommissionSchemeRepository schemes;

    public CommissionSchemeResolver(CommissionSchemeRepository schemes) {
        this.schemes = schemes;
    }

    @Transactional(readOnly = true)
    public CommissionScheme resolveEffective(LocalDate date) {
        List<CommissionScheme> effective = schemes.findByOrderByEffectiveFromDesc()
                .stream()
                .filter(scheme -> !scheme.getEffectiveFrom().isAfter(date))
                .filter(scheme -> scheme.getEffectiveTo() == null || !date.isAfter(scheme.getEffectiveTo()))
                .toList();

        if (effective.isEmpty()) {
            throw new IllegalStateException("ไม่พบ Commission Scheme ที่มีผลในวันที่ " + date);
        }

        if (effective.size() > 1) {
            throw new IllegalStateException("พบ Commission Scheme ที่มีผลในวันที่ " + date + " มากกว่าหนึ่งรายการ");
        }

        return effective.get(0);
    }
}
