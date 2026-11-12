package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Picks the one enabled Commission Tier whose inclusive [minimum, maximum] range covers the monthly sales, compared
 * at 2 decimals (50,000.00 is in one tier, 50,000.01 and 50,000.50 in the next).
 */
@Service
public class CommissionTierResolver {
    private final CommissionTierRepository tiers;

    public CommissionTierResolver(CommissionTierRepository tiers) {
        this.tiers = tiers;
    }

    @Transactional(readOnly = true)
    public CommissionTier resolve(
            CommissionScheme scheme,
            BigDecimal monthlySales) {
        BigDecimal sales = monthlySales.setScale(2, RoundingMode.HALF_UP);
        List<CommissionTier> covering = tiers.findByCommissionSchemeIdOrderByTierOrder(scheme.getId())
                .stream()
                .filter(tier -> sales.compareTo(tier.getMinimumMonthlySales()) >= 0
                        && (tier.getMaximumMonthlySales() == null
                            || sales.compareTo(tier.getMaximumMonthlySales()) <= 0))
                .toList();

        if (covering.isEmpty()) {
            throw new IllegalStateException("ไม่พบ Commission Tier ที่ครอบคลุมยอดขาย " + sales);
        }

        if (covering.size() > 1) {
            throw new IllegalStateException("พบ Commission Tier ที่ครอบคลุมยอดขาย " + sales + " มากกว่าหนึ่งรายการ");
        }

        return covering.get(0);
    }
}
