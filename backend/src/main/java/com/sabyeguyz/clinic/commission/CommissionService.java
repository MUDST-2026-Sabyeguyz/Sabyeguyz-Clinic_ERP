package com.sabyeguyz.clinic.commission;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommissionService {
    private final CommissionSchemeRepository schemes;
    private final CommissionTierRepository tiers;

    public CommissionService(CommissionSchemeRepository schemes, CommissionTierRepository tiers) {
        this.schemes = schemes; this.tiers = tiers;
    }

    @Transactional(readOnly = true)
    public List<CommissionScheme> listSchemes() { return schemes.findByOrderByEffectiveFromDesc(); }

    @Transactional(readOnly = true)
    public List<CommissionTier> tiers(long schemeId) { return tiers.findBySchemeIdAndActiveTrueOrderByTierOrder(schemeId); }

    @Transactional
    public CommissionScheme create(String name, String version, LocalDate from, LocalDate to, List<TierInput> inputs) {
        if (name == null || name.isBlank() || version == null || version.isBlank() || from == null) throw new IllegalArgumentException("scheme metadata is required");
        if (to != null && !to.isAfter(from)) throw new IllegalArgumentException("effective_to must be after effective_from");
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("at least one commission tier is required");
        validateTiers(inputs);
        CommissionScheme scheme = schemes.save(new CommissionScheme(name.trim(), version.trim(), from, to));
        inputs.stream().sorted(Comparator.comparingInt(TierInput::order)).map(i -> new CommissionTier(scheme, i.minimum(), i.maximum(), i.rate(), i.order())).forEach(tiers::save);
        return scheme;
    }

    @Transactional(readOnly = true)
    public CommissionTier resolve(LocalDate date, BigDecimal monthlySales) {
        return schemes.findByOrderByEffectiveFromDesc().stream()
                .filter(s -> s.isActive() && !s.getEffectiveFrom().isAfter(date)
                        && (s.getEffectiveTo() == null || date.isBefore(s.getEffectiveTo())))
                .findFirst().flatMap(s -> tiers.findBySchemeIdAndActiveTrueOrderByTierOrder(s.getId()).stream()
                        .filter(t -> monthlySales.compareTo(t.getMinimumSales()) >= 0
                                && (t.getMaximumSales() == null || monthlySales.compareTo(t.getMaximumSales()) <= 0)).findFirst())
                .orElseThrow(() -> new IllegalArgumentException("no commission tier covers the monthly sales amount"));
    }

    private void validateTiers(List<TierInput> inputs) {
        List<TierInput> ordered = inputs.stream().sorted(Comparator.comparingInt(TierInput::order)).toList();
        BigDecimal expectedMinimum = BigDecimal.ZERO;
        for (TierInput tier : ordered) {
            if (tier.order() < 1 || tier.minimum() == null || tier.minimum().compareTo(BigDecimal.ZERO) < 0
                    || tier.rate() == null || tier.rate().compareTo(BigDecimal.ZERO) < 0 || tier.rate().compareTo(BigDecimal.ONE) > 0)
                throw new IllegalArgumentException("invalid commission tier");
            if (tier.maximum() != null && tier.maximum().compareTo(tier.minimum()) < 0) throw new IllegalArgumentException("tier minimum exceeds maximum");
            if (tier.minimum().compareTo(expectedMinimum) != 0) throw new IllegalArgumentException("commission tier ranges must be contiguous and non-overlapping");
            if (tier.maximum() == null && tier != ordered.get(ordered.size() - 1)) throw new IllegalArgumentException("open-ended tier must be last");
            if (tier.maximum() != null) expectedMinimum = tier.maximum().add(new BigDecimal("0.01"));
        }
    }

    public record TierInput(BigDecimal minimum, BigDecimal maximum, BigDecimal rate, int order) {}
}
