package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.catalog.CourseRepository;
import com.sabyeguyz.clinicv2.role.Permission;

/**
 * Commission Scheme + Tier management (Data Dictionary: Commission_scheme, Commission_tier).
 *
 * <ul>
 *   <li>Tier ranges are in baht with 2 decimals, inclusive at both ends, with no gap and no overlap: the tier after
 *       a maximum of 50,000.00 starts at 50,000.01, and 50,000.50 belongs to the next tier. The first tier starts at
 *       0.00 and the top tier has no maximum.</li>
 *   <li>The date ranges of schemes never overlap, so a reference date finds one scheme. A new open-ended version
 *       takes over an open-ended predecessor by ending it the day before.</li>
 *   <li>A scheme already used by a course or a monthly closing is immutable (tiers, start date); a change of ranges
 *       or rates is a new version.</li>
 * </ul>
 */
@Service
public class CommissionSchemeService {
    private static final BigDecimal ONE_SATANG = new BigDecimal("0.01");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999999.99");

    private final CommissionSchemeRepository schemes;
    private final CommissionTierRepository tiers;
    private final CourseRepository courses;
    private final MonthlyCommissionClosingRepository closings;
    private final BranchClosingGuard branchClosingGuard;

    private com.sabyeguyz.clinicv2.audit.AuditLogService audit;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAudit(com.sabyeguyz.clinicv2.audit.AuditLogService audit) {
        this.audit = audit;
    }

    public CommissionSchemeService(
            CommissionSchemeRepository schemes,
            CommissionTierRepository tiers,
            CourseRepository courses,
            MonthlyCommissionClosingRepository closings,
            BranchClosingGuard branchClosingGuard) {
        this.schemes = schemes;
        this.tiers = tiers;
        this.courses = courses;
        this.closings = closings;
        this.branchClosingGuard = branchClosingGuard;
    }

    @Transactional(readOnly = true)
    public List<CommissionScheme> list() {
        return schemes.findByOrderByEffectiveFromDesc();
    }

    @Transactional(readOnly = true)
    public List<CommissionTier> tiersOf(Long schemeId) {
        return tiers.findByCommissionSchemeIdOrderByTierOrder(schemeId);
    }

    /** Whether a course or a monthly closing already points at the scheme (it is then immutable). */
    @Transactional(readOnly = true)
    public boolean isReferenced(Long schemeId) {
        return courses.existsByCommissionSchemeId(schemeId) || closings.existsByCommissionSchemeId(schemeId);
    }

    public record TierInput(
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal rate,
            int order) {}

    @Transactional
    public CommissionScheme create(
            String schemeName,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            com.sabyeguyz.clinicv2.branch.Branch branch,
            Permission permission,
            List<TierInput> tierInputs,
            Long actorId) {
        validate(schemeName, effectiveFrom, effectiveTo, tierInputs);
        branchClosingGuard.requireCurrentMonthClosed(branch);
        validateTiers(tierInputs);
        schemes.lockVersionAllocation();
        requireNoOtherPending(null, effectiveFrom);
        retireOverlapping(effectiveFrom, effectiveTo, actorId);

        String name = schemeName.trim();
        int nextVersion = schemes.findByOrderByEffectiveFromDesc().stream()
                .mapToInt(CommissionScheme::getSchemeVersion).max().orElse(0) + 1;

        CommissionScheme scheme = new CommissionScheme();
        scheme.setSchemeName(name);
        scheme.setSchemeVersion(nextVersion);
        scheme.setPermission(permission);
        scheme.setEffectiveFrom(effectiveFrom);
        scheme.setEffectiveTo(effectiveTo);
        scheme.setSchemeCreateAt(Instant.now());
        scheme.setSchemeCreateBy(actorId);
        CommissionScheme saved = schemes.save(scheme);
        saveTiers(saved, permission, tierInputs, actorId);

        return saved;
    }

    /**
     * Edits a scheme. A scheme nothing refers to yet can change everything (tiers are replaced when given); one that
     * is in use keeps its tiers and start date, and only its name and end date may change.
     */
    @Transactional
    public CommissionScheme update(
            Long id,
            String schemeName,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            com.sabyeguyz.clinicv2.branch.Branch branch,
            List<TierInput> tierInputs,
            Long actorId) {
        branchClosingGuard.requireCurrentMonthClosed(branch);
        CommissionScheme scheme = find(id);

        if (tierInputs != null && tierInputs.isEmpty()) {
            tierInputs = null; // an empty list means "tiers unchanged"
        }

        boolean referenced = isReferenced(id);
        LocalDate newFrom = effectiveFrom != null ? effectiveFrom : scheme.getEffectiveFrom();
        // an omitted end date keeps the current one; a scheme is reopened by creating a new version
        LocalDate newTo = effectiveTo != null ? effectiveTo : scheme.getEffectiveTo();

        if (referenced) {
            if (tierInputs != null) {
                throw new IllegalStateException("Scheme นี้ถูกใช้งานแล้ว ห้ามแก้ Tier ให้สร้าง Scheme เวอร์ชันใหม่");
            }

            if (!newFrom.equals(scheme.getEffectiveFrom())) {
                throw new IllegalStateException("Scheme นี้ถูกใช้งานแล้ว ห้ามเปลี่ยนวันที่เริ่มมีผล ให้สร้าง Scheme เวอร์ชันใหม่");
            }

            if (newTo != null && courses.existsBySchemeUsedAfter(id, newTo)) {
                throw new IllegalStateException("มีคอร์สที่ใช้ Scheme นี้หลังวันที่สิ้นสุดที่ระบุ");
            }
        }

        if (newTo != null && newTo.isBefore(newFrom)) {
            throw new IllegalArgumentException("effective_to ต้องไม่อยู่ก่อน effective_from");
        }

        if (tierInputs != null) {
            validateTiers(tierInputs);
        }
        // A version that has not started may move to another month. The version before it ended the day before this one
        // started, so it follows: it ends the day before the new start. Cycles already closed are never touched.
        CommissionScheme predecessor = null;
        LocalDate oldFrom = scheme.getEffectiveFrom();

        if (!referenced && !newFrom.equals(oldFrom)) {
            requireFutureCycleStart(newFrom);
            predecessor = schemes.findByOrderByEffectiveFromDesc().stream()
                    .filter(other -> !other.getId().equals(id) && oldFrom.minusDays(1).equals(other.getEffectiveTo()))
                    .findFirst().orElse(null);

            if (predecessor != null) {
                if (!newFrom.isAfter(predecessor.getEffectiveFrom())) {
                    throw new IllegalArgumentException("เดือนที่เริ่มต้องอยู่หลังเวอร์ชันก่อนหน้า (เริ่ม " + predecessor.getEffectiveFrom() + ")");
                }

                if (newFrom.isBefore(oldFrom)
                        && courses.existsBySchemeUsedAfter(predecessor.getId(), newFrom.minusDays(1))) {
                    throw new IllegalStateException("เวอร์ชันก่อนหน้าถูกใช้กับคอร์สที่ขายตั้งแต่วันที่ " + newFrom
                            + " แล้ว จึงสิ้นสุดก่อนวันนั้นไม่ได้");
                }
            }
        }

        requireNoOverlap(id, predecessor == null ? null : predecessor.getId(), newFrom, newTo);

        if (schemeName != null && !schemeName.isBlank()) {
            scheme.setSchemeName(schemeName.trim());
        }

        scheme.setEffectiveFrom(newFrom);
        scheme.setEffectiveTo(newTo);
        scheme.setSchemeUpdateAt(Instant.now());
        scheme.setSchemeUpdateBy(actorId);
        CommissionScheme saved = schemes.save(scheme);

        if (predecessor != null) endBefore(predecessor, newFrom, "the next version now starts " + newFrom, actorId);

        if (tierInputs != null) {
            tiers.deleteAll(tiers.findByCommissionSchemeIdOrderByTierOrder(id));
            tiers.flush();
            saveTiers(saved, scheme.getPermission(), tierInputs, actorId);
        } else {
            for (CommissionTier tier : tiers.findByCommissionSchemeIdOrderByTierOrder(id)) {
                tier.setEffectiveFrom(newFrom);
                tier.setEffectiveTo(newTo);
                tier.setTierUpdateAt(Instant.now());
                tier.setTierUpdateBy(actorId);
                tiers.save(tier);
            }
        }

        return saved;
    }

    /** A new version of an existing scheme with new tiers; it takes over from {@code effectiveFrom}. */
    @Transactional
    public CommissionScheme createVersion(
            Long id,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            com.sabyeguyz.clinicv2.branch.Branch branch,
            List<TierInput> tierInputs,
            Long actorId) {
        CommissionScheme base = find(id);

        return create(
                base.getSchemeName(),
                effectiveFrom,
                effectiveTo,
                branch,
                base.getPermission(),
                tierInputs,
                actorId);
    }

    /** Removes an unused scheme; a scheme already referenced by a course or closing must remain immutable. */
    @Transactional
    public void deactivate(Long id) {
        CommissionScheme scheme = find(id);

        if (isReferenced(id)) {
            throw new IllegalStateException("Scheme นี้ถูกใช้งานแล้ว ปิดใช้งานไม่ได้ ให้สร้างเวอร์ชันใหม่แทน");
        }

        tiers.deleteAll(tiers.findByCommissionSchemeIdOrderByTierOrder(id));
        schemes.delete(scheme);
    }

    /**
     * Validates the effective date.
     * The selected branch closing is checked by create/update.
     */
    private void requireNextCycle(LocalDate effectiveFrom) {
        requireFutureCycleStart(effectiveFrom);
    }

    private void saveTiers(
            CommissionScheme scheme,
            Permission permission,
            List<TierInput> inputs,
            Long actorId) {
        inputs.stream()
                .sorted(Comparator.comparingInt(TierInput::order)).forEach(input -> {
            CommissionTier tier = new CommissionTier();
            tier.setCommissionScheme(scheme);
            tier.setPermission(permission);
            tier.setTierOrder(input.order());
            tier.setMinimumMonthlySales(input.minimum().setScale(2));
            tier.setMaximumMonthlySales(input.maximum() == null ? null : input.maximum().setScale(2));
            tier.setCommissionRate(input.rate());
            tier.setEffectiveFrom(scheme.getEffectiveFrom());
            tier.setEffectiveTo(scheme.getEffectiveTo());
            tier.setTierCreateAt(Instant.now());
            tier.setTierCreateBy(actorId);
            tiers.save(tier);
        });
    }

    /** A version starts on the 1st of a month after the current one: the rules never change in the middle of a month. */
    private static void requireFutureCycleStart(LocalDate effectiveFrom) {
        if (effectiveFrom == null) throw new IllegalArgumentException("ต้องระบุ effective_from");

        if (effectiveFrom.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("เวอร์ชันใหม่ต้องเริ่มวันที่ 1 ของเดือน เพื่อให้ใช้ทั้งรอบ");
        }

        java.time.YearMonth current = java.time.YearMonth.from(com.sabyeguyz.clinicv2.config.ClinicTime.today());

        if (!java.time.YearMonth.from(effectiveFrom).isAfter(current)) {
            throw new IllegalArgumentException("เวอร์ชันใหม่ใช้ตั้งแต่รอบถัดไป ต้องเริ่มหลังเดือน " + current);
        }
    }

    /** Only one version waits to start: a change while one is waiting is made on that version. */
    private void requireNoOtherPending(
            Long selfId,
            LocalDate from) {
        LocalDate today = com.sabyeguyz.clinicv2.config.ClinicTime.today();

        if (from == null || !from.isAfter(today)) return;
        boolean other = schemes.findByOrderByEffectiveFromDesc().stream()
                .anyMatch(scheme -> !scheme.getId().equals(selfId) && scheme.getEffectiveFrom().isAfter(today));

        if (other) {
            throw new IllegalStateException("มีเวอร์ชันที่รอเริ่มใช้อยู่แล้ว ให้แก้เวอร์ชันนั้นแทนการสร้างเพิ่ม");
        }
    }

    /** Ends a scheme (and its tiers) the day before {@code newStart}; the change is written to the audit log. */
    private void endBefore(
            CommissionScheme other,
            LocalDate newStart,
            String reason,
            Long actorId) {
        LocalDate previousEnd = other.getEffectiveTo();
        other.setEffectiveTo(newStart.minusDays(1));
        other.setSchemeUpdateAt(Instant.now());
        other.setSchemeUpdateBy(actorId);
        schemes.save(other);

        for (CommissionTier tier : tiers.findByCommissionSchemeIdOrderByTierOrder(other.getId())) {
            tier.setEffectiveTo(newStart.minusDays(1));
            tier.setTierUpdateAt(Instant.now());
            tier.setTierUpdateBy(actorId);
            tiers.save(tier);
        }

        if (audit != null) {
            audit.recordChange("commission_scheme", String.valueOf(other.getId()), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                    com.sabyeguyz.clinicv2.audit.AuditLogService.fields("effective_to", previousEnd),
                    com.sabyeguyz.clinicv2.audit.AuditLogService.fields(
                            "effective_to",
                            other.getEffectiveTo(),
                            "reason",
                            reason),
                    actorId);
        }
    }

    /**
     * Ends an open-ended scheme that the new range takes over from. Any other overlap is rejected, so one date never
     * has two schemes.
     */
    private void retireOverlapping(
            LocalDate from,
            LocalDate to,
            Long actorId) {
        for (CommissionScheme other : schemes.findByOrderByEffectiveFromDesc()) {
            if (!overlaps(other.getEffectiveFrom(), other.getEffectiveTo(), from, to)) {
                continue;
            }

            boolean takesOver = to == null && other.getEffectiveTo() == null && other.getEffectiveFrom().isBefore(from);

            if (!takesOver) {
                throw new IllegalArgumentException("ช่วงวันที่ของ Scheme ซ้อนกับ \"" + other.getSchemeName() + "\" เวอร์ชัน "
                        + other.getSchemeVersion() + " ที่มีผลอยู่แล้ว");
            }
            // new tiers replace the rules that are live now: only from a later cycle, after the current one is closed
            requireNextCycle(from);

            if (courses.existsBySchemeUsedAfter(other.getId(), from.minusDays(1))) {
                throw new IllegalStateException("Scheme \"" + other.getSchemeName() + "\" เวอร์ชัน " + other.getSchemeVersion()
                        + " ถูกใช้กับคอร์สที่ขายตั้งแต่วันที่ " + from + " แล้ว จึงสิ้นสุดก่อนวันนั้นไม่ได้");
            }

            LocalDate previousEnd = other.getEffectiveTo();
            other.setEffectiveTo(from.minusDays(1));
            other.setSchemeUpdateAt(Instant.now());
            other.setSchemeUpdateBy(actorId);
            schemes.save(other);

            if (audit != null) {
                audit.recordChange("commission_scheme", String.valueOf(other.getId()), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                        com.sabyeguyz.clinicv2.audit.AuditLogService.fields("effective_to", previousEnd),
                        com.sabyeguyz.clinicv2.audit.AuditLogService.fields("effective_to", other.getEffectiveTo(),
                                "reason", "ended by the new version starting " + from), actorId);
            }

            for (CommissionTier tier : tiers.findByCommissionSchemeIdOrderByTierOrder(other.getId())) {
                tier.setEffectiveTo(from.minusDays(1));
                tier.setTierUpdateAt(Instant.now());
                tier.setTierUpdateBy(actorId);
                tiers.save(tier);
            }
        }
    }

    private void requireNoOverlap(
            Long selfId,
            Long alsoIgnoreId,
            LocalDate from,
            LocalDate to) {
        for (CommissionScheme other : schemes.findByOrderByEffectiveFromDesc()) {
            if (other.getId().equals(alsoIgnoreId)) continue;

            if (!other.getId().equals(selfId)
                    && overlaps(
                    other.getEffectiveFrom(),
                    other.getEffectiveTo(),
                    from,
                    to)) {
                throw new IllegalArgumentException("ช่วงวันที่ของ Scheme ซ้อนกับ \"" + other.getSchemeName() + "\" เวอร์ชัน "
                        + other.getSchemeVersion());
            }
        }
    }

    /** Inclusive ranges; a null end is open. */
    private static boolean overlaps(
            LocalDate aFrom,
            LocalDate aTo,
            LocalDate bFrom,
            LocalDate bTo) {
        boolean aStartsBeforeBEnds = bTo == null || !aFrom.isAfter(bTo);
        boolean bStartsBeforeAEnds = aTo == null || !bFrom.isAfter(aTo);

        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    private CommissionScheme find(Long id) {
        return schemes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบ Commission Scheme"));
    }

    private static void validate(
            String name,
            LocalDate from,
            LocalDate to,
            List<TierInput> tiers) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("ต้องระบุชื่อ Scheme");

        if (from == null) throw new IllegalArgumentException("ต้องระบุ effective_from");

        if (to != null && to.isBefore(from)) throw new IllegalArgumentException("effective_to ต้องไม่อยู่ก่อน effective_from");

        if (tiers == null || tiers.isEmpty()) throw new IllegalArgumentException("ต้องมีอย่างน้อย 1 Tier");
    }

    /**
     * Tier ranges in baht with 2 decimals, inclusive at both ends: the first tier starts at 0.00, each next tier starts
     * exactly 0.01 above the previous maximum (no gap, no overlap), and the top tier has no maximum. Tier orders are
     * 1 or more and unique; rates are between 0 and 1.
     */
    static void validateTiers(List<TierInput> inputs) {
        Set<Integer> orders = new HashSet<>();

        for (TierInput tier : inputs) {
            if (tier.order() < 1) throw new IllegalArgumentException("tier_order ต้องมากกว่าหรือเท่ากับ 1");

            if (!orders.add(tier.order())) throw new IllegalArgumentException("tier_order ซ้ำกัน: " + tier.order());
        }

        List<TierInput> ordered = inputs.stream()
                .sorted(Comparator.comparingInt(TierInput::order))
                .toList();
        BigDecimal previousMaximum = null;

        for (int i = 0; i < ordered.size(); i++) {
            TierInput tier = ordered.get(i);

            if (tier.minimum() == null || tier.minimum().signum() < 0) {
                throw new IllegalArgumentException("minimum_monthly_sales ต้องไม่ติดลบ");
            }

            requireTwoDecimals(tier.minimum(), "minimum_monthly_sales");

            if (tier.rate() == null
                    || tier.rate().signum() < 0
                    || tier.rate().compareTo(BigDecimal.ONE) > 0
                    || tier.rate().stripTrailingZeros().scale() > 4) {
                throw new IllegalArgumentException("commission_rate ต้องอยู่ระหว่าง 0 ถึง 1 และมีทศนิยมไม่เกิน 4 ตำแหน่ง");
            }

            if (tier.maximum() != null) {
                requireTwoDecimals(tier.maximum(), "maximum_monthly_sales");

                if (tier.maximum().compareTo(tier.minimum()) < 0) {
                    throw new IllegalArgumentException("maximum_monthly_sales ต้องไม่น้อยกว่า minimum_monthly_sales");
                }
            }

            if (previousMaximum == null) {
                if (tier.minimum().signum() != 0) {
                    throw new IllegalArgumentException("Tier แรกต้องเริ่มที่ 0.00");
                }
            } else if (tier.minimum().compareTo(previousMaximum.add(ONE_SATANG)) != 0) {
                throw new IllegalArgumentException("Tier ถัดจาก " + previousMaximum.setScale(2) + " ต้องเริ่มที่ "
                        + previousMaximum.add(ONE_SATANG).setScale(2) + " (ไม่มีช่องว่างหรือช่วงซ้อน)");
            }

            if (tier.maximum() == null && i != ordered.size() - 1) {
                throw new IllegalArgumentException("Tier ที่ไม่มีเพดานต้องเป็น Tier สุดท้ายเท่านั้น");
            }

            previousMaximum = tier.maximum();
        }

        if (previousMaximum != null) {
            throw new IllegalArgumentException("Tier สุดท้ายต้องไม่มีเพดาน (maximum_monthly_sales เป็นค่าว่าง) เพื่อครอบคลุมทุกยอดขาย");
        }
    }

    private static void requireTwoDecimals(
            BigDecimal value,
            String field) {
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(field + " ต้องมีทศนิยมไม่เกิน 2 ตำแหน่ง");
        }

        if (value.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException(field + " มีค่ามากเกินไป");
        }
    }
}
