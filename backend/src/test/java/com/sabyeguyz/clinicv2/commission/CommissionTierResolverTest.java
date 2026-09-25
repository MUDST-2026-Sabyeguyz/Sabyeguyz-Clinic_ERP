package com.sabyeguyz.clinicv2.commission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Uses the default tiers from Commission_System_Requirement_Dev §2. */
@ExtendWith(MockitoExtension.class)
class CommissionTierResolverTest {
    @Mock CommissionTierRepository repository;

    private static CommissionTier tier(int order, String min, String max, String rate) {
        CommissionTier tier = new CommissionTier();
        tier.setTierOrder(order);
        tier.setMinimumMonthlySales(new BigDecimal(min));
        tier.setMaximumMonthlySales(max == null ? null : new BigDecimal(max));
        tier.setCommissionRate(new BigDecimal(rate));
        return tier;
    }

    private List<CommissionTier> defaultTiers() {
        return List.of(
                tier(1, "0", "59999", "0.05"),
                tier(2, "60000", "69999", "0.06"),
                tier(3, "70000", "79999", "0.07"),
                tier(4, "80000", "89999", "0.08"),
                tier(5, "90000", null, "0.10"));
    }

    @Test
    void resolvesTheSeventyPercentTierFromTheRequirementDocExample() {
        CommissionScheme scheme = new CommissionScheme();
        when(repository.findByCommissionSchemeIdAndTierStatusTrueOrderByTierOrder(null)).thenReturn(defaultTiers());

        CommissionTier resolved = new CommissionTierResolver(repository).resolve(scheme, new BigDecimal("75000"));

        assertThat(resolved.getCommissionRate()).isEqualByComparingTo("0.07");
    }

    @Test
    void openEndedTopTierHasNoUpperBound() {
        CommissionScheme scheme = new CommissionScheme();
        when(repository.findByCommissionSchemeIdAndTierStatusTrueOrderByTierOrder(null)).thenReturn(defaultTiers());

        CommissionTier resolved = new CommissionTierResolver(repository).resolve(scheme, new BigDecimal("500000"));

        assertThat(resolved.getCommissionRate()).isEqualByComparingTo("0.10");
    }

    @Test
    void throwsWhenNoTierCoversTheAmount() {
        CommissionScheme scheme = new CommissionScheme();
        when(repository.findByCommissionSchemeIdAndTierStatusTrueOrderByTierOrder(null)).thenReturn(List.of());

        assertThatThrownBy(() -> new CommissionTierResolver(repository).resolve(scheme, new BigDecimal("1000")))
                .isInstanceOf(IllegalStateException.class);
    }
}
