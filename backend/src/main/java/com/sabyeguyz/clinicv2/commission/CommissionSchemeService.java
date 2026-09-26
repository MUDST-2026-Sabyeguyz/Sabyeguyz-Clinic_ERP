package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.role.Permission;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commission Scheme + Tier management (Commission_System_Requirement_Dev
 * §2, §23-24 "Tier Versioning"). Editing an existing scheme's tiers is not
 * supported here — per §24, a change to ranges/rates should create a new
 * Scheme version instead of mutating history, so Courses already frozen
 * against an older version never move. {@code update} therefore only
 * touches the scheme's own name/effective dates, never its tiers.
 */
@Service
public class CommissionSchemeService {
    private final CommissionSchemeRepository schemes;
    private final CommissionTierRepository tiers;

    public CommissionSchemeService(CommissionSchemeRepository schemes, CommissionTierRepository tiers) {
        this.schemes = schemes;
        this.tiers = tiers;
    }

    @Transactional(readOnly = true)
    public List<CommissionScheme> list() {
        return schemes.findByOrderByEffectiveFromDesc();
    }

    @Transactional(readOnly = true)
    public List<CommissionTier> tiersOf(Long schemeId) {
        return tiers.findByCommissionSchemeIdAndTierStatusTrueOrderByTierOrder(schemeId);
    }

    public record TierInput(BigDecimal minimum, BigDecimal maximum, BigDecimal rate, int order) {}

    @Transactional
    public CommissionScheme create(String schemeName, LocalDate effectiveFrom, LocalDate effectiveTo,
                                    Permission permission, List<TierInput> tierInputs, Long actorId) {
        validate(schemeName, effectiveFrom, effectiveTo, tierInputs);
        validateTiers(tierInputs);

        int nextVersion = schemes.findByOrderByEffectiveFromDesc().stream()
                .mapToInt(CommissionScheme::getSchemeVersion).max().orElse(0) + 1;

        CommissionScheme scheme = new CommissionScheme();
        scheme.setSchemeName(schemeName.trim());
        scheme.setSchemeVersion(nextVersion);
        scheme.setPermission(permission);
        scheme.setEffectiveFrom(effectiveFrom);
        scheme.setEffectiveTo(effectiveTo);
        scheme.setSchemeCreateAt(Instant.now());
        scheme.setSchemeCreateBy(actorId);
        CommissionScheme saved = schemes.save(scheme);

        tierInputs.stream().sorted(Comparator.comparingInt(TierInput::order)).forEach(input -> {
            CommissionTier tier = new CommissionTier();
            tier.setCommissionScheme(saved);
            tier.setPermission(permission);
            tier.setTierOrder(input.order());
            tier.setMinimumMonthlySales(input.minimum());
            tier.setMaximumMonthlySales(input.maximum());
            tier.setCommissionRate(input.rate());
            tier.setTierStatus(true);
            tier.setEffectiveFrom(effectiveFrom);
            tier.setEffectiveTo(effectiveTo);
            tier.setTierCreateAt(Instant.now());
            tier.setTierCreateBy(actorId);
            tiers.save(tier);
        });

        return saved;
    }

    @Transactional
    public CommissionScheme update(Long id, String schemeName, LocalDate effectiveFrom, LocalDate effectiveTo, Long actorId) {
        CommissionScheme scheme = find(id);
        if (schemeName != null && !schemeName.isBlank()) {
            scheme.setSchemeName(schemeName.trim());
        }
        if (effectiveFrom != null) scheme.setEffectiveFrom(effectiveFrom);
        scheme.setEffectiveTo(effectiveTo);
        scheme.setSchemeUpdateAt(Instant.now());
        scheme.setSchemeUpdateBy(actorId);
        return schemes.save(scheme);
    }

    @Transactional
    public void activate(Long id) {
        // v2's Commission_scheme has no status column - only Commission_tier does.
        // Activating a scheme activates every tier belonging to it.
        setTiersStatus(id, true);
    }

    @Transactional
    public void deactivate(Long id) {
        setTiersStatus(id, false);
    }

    private void setTiersStatus(Long schemeId, boolean status) {
        find(schemeId);
        tiers.findAll().stream()
                .filter(t -> t.getCommissionScheme().getId().equals(schemeId))
                .forEach(t -> { t.setTierStatus(status); tiers.save(t); });
    }

    private CommissionScheme find(Long id) {
        return schemes.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบ Commission Scheme"));
    }

    private static void validate(String name, LocalDate from, LocalDate to, List<TierInput> tiers) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("ต้องระบุชื่อ Scheme");
        if (from == null) throw new IllegalArgumentException("ต้องระบุ effective_from");
        if (to != null && !to.isAfter(from)) throw new IllegalArgumentException("effective_to ต้องอยู่หลัง effective_from");
        if (tiers == null || tiers.isEmpty()) throw new IllegalArgumentException("ต้องมีอย่างน้อย 1 Tier");
    }

    /** Enforces Commission_System_Requirement_Dev §2: no overlap, no gap, min<=max, rate>=0. */
    private static void validateTiers(List<TierInput> inputs) {
        List<TierInput> ordered = inputs.stream().sorted(Comparator.comparingInt(TierInput::order)).toList();
        BigDecimal expectedMinimum = BigDecimal.ZERO;
        for (int i = 0; i < ordered.size(); i++) {
            TierInput tier = ordered.get(i);
            if (tier.minimum() == null || tier.minimum().signum() < 0) {
                throw new IllegalArgumentException("minimum_monthly_sales ต้องไม่ติดลบ");
            }
            if (tier.rate() == null || tier.rate().signum() < 0) {
                throw new IllegalArgumentException("commission_rate ต้องไม่ติดลบ");
            }
            if (tier.maximum() != null && tier.maximum().compareTo(tier.minimum()) < 0) {
                throw new IllegalArgumentException("maximum_monthly_sales ต้องไม่น้อยกว่า minimum_monthly_sales");
            }
            if (tier.minimum().compareTo(expectedMinimum) != 0) {
                throw new IllegalArgumentException("ช่วงยอดขายของ Tier ต้องต่อเนื่องกันโดยไม่ซ้อนหรือมีช่องว่าง");
            }
            if (tier.maximum() == null && i != ordered.size() - 1) {
                throw new IllegalArgumentException("Tier ที่ไม่มีเพดานต้องเป็น Tier สุดท้ายเท่านั้น");
            }
            if (tier.maximum() != null) {
                expectedMinimum = tier.maximum().add(new BigDecimal("0.01"));
            }
        }
    }
}
