package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks the Commission Tier whose [minimum, maximum] monthly-sales range
 * covers a given amount (Commission_System_Requirement_Dev §2).
 */
@Service
public class CommissionTierResolver {
    private final CommissionTierRepository tiers;

    public CommissionTierResolver(CommissionTierRepository tiers) {
        this.tiers = tiers;
    }

    @Transactional(readOnly = true)
    public CommissionTier resolve(CommissionScheme scheme, BigDecimal monthlySales) {
        return tiers.findByCommissionSchemeIdAndTierStatusTrueOrderByTierOrder(scheme.getId()).stream()
                .filter(tier -> monthlySales.compareTo(tier.getMinimumMonthlySales()) >= 0
                        && (tier.getMaximumMonthlySales() == null
                            || monthlySales.compareTo(tier.getMaximumMonthlySales()) <= 0))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ไม่พบ Commission Tier ที่ครอบคลุมยอดขาย " + monthlySales));
    }
}
