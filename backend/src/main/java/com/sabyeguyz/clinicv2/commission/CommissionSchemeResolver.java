package com.sabyeguyz.clinicv2.commission;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks the Commission Scheme effective on a given date
 * (Commission_System_Requirement_Dev §24 "Tier Versioning").
 */
@Service
public class CommissionSchemeResolver {
    private final CommissionSchemeRepository schemes;

    public CommissionSchemeResolver(CommissionSchemeRepository schemes) {
        this.schemes = schemes;
    }

    @Transactional(readOnly = true)
    public CommissionScheme resolveEffective(LocalDate date) {
        return schemes.findByOrderByEffectiveFromDesc().stream()
                .filter(scheme -> !scheme.getEffectiveFrom().isAfter(date))
                .filter(scheme -> scheme.getEffectiveTo() == null || date.isBefore(scheme.getEffectiveTo()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ไม่พบ Commission Scheme ที่มีผลในวันที่ " + date));
    }
}
