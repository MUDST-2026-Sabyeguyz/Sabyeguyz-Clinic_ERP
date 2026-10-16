package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.role.Permission;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin of the one clinic-wide Treatment Fee Rule (Data Dictionary: Treatment_fee_rule). A day matches exactly one
 * enabled rule, so the date ranges of enabled rules never overlap. A rule that a ledger row already copied is
 * immutable in its type, value and start: a new rate is a new rule with its own effective date.
 */
@Service
public class TreatmentFeeRuleService {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TreatmentFeeRuleRepository rules;

    public TreatmentFeeRuleService(TreatmentFeeRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public List<TreatmentFeeRule> list() {
        return rules.findAllByOrderByEffectiveFromDesc();
    }

    @Transactional(readOnly = true)
    public boolean isUsed(Long ruleId) {
        return rules.isUsedByLedger(ruleId);
    }

    @Transactional
    public TreatmentFeeRule create(FeeType feeType, BigDecimal feeValue, LocalDate effectiveFrom, LocalDate effectiveTo,
                                    Permission permission, Long actorId) {
        validate(feeType, feeValue, effectiveFrom, effectiveTo);
        retireOverlapping(null, effectiveFrom, effectiveTo, actorId);

        TreatmentFeeRule rule = new TreatmentFeeRule();
        rule.setPermission(permission);
        rule.setFeeType(feeType);
        rule.setFeeValue(feeValue);
        rule.setEffectiveFrom(effectiveFrom);
        rule.setEffectiveTo(effectiveTo);
        rule.setTreatmentFeeRuleStatus(true);
        rule.setTreatmentFeeRuleCreateAt(Instant.now());
        rule.setTreatmentFeeRuleCreateBy(actorId);
        return rules.save(rule);
    }

    /** A rule in use may only end earlier (or later), never change what it pays. */
    @Transactional
    public TreatmentFeeRule update(Long id, FeeType feeType, BigDecimal feeValue, LocalDate effectiveFrom,
                                    LocalDate effectiveTo, Long actorId) {
        TreatmentFeeRule rule = find(id);
        FeeType newType = feeType != null ? feeType : rule.getFeeType();
        BigDecimal newValue = feeValue != null ? feeValue : rule.getFeeValue();
        LocalDate newFrom = effectiveFrom != null ? effectiveFrom : rule.getEffectiveFrom();
        // an omitted end date keeps the current one; a rule is reopened by creating a new rule
        LocalDate newTo = effectiveTo != null ? effectiveTo : rule.getEffectiveTo();
        validate(newType, newValue, newFrom, newTo);

        if (isUsed(id)) {
            boolean changesPayment = newType != rule.getFeeType() || newValue.compareTo(rule.getFeeValue()) != 0
                    || !newFrom.equals(rule.getEffectiveFrom());
            if (changesPayment) {
                throw new IllegalStateException("กฎค่ามือนี้ถูกใช้คำนวณไปแล้ว ห้ามแก้ประเภท อัตรา หรือวันที่เริ่ม ให้สร้างกฎใหม่ตามวันที่มีผล");
            }
            if (newTo != null && rules.isUsedAfter(id, newTo)) {
                throw new IllegalStateException("มีรายการรักษาที่ใช้กฎนี้หลังวันที่สิ้นสุดที่ระบุ");
            }
        }
        if (rule.isTreatmentFeeRuleStatus()) {
            requireNoOverlap(id, newFrom, newTo);
        }
        rule.setFeeType(newType);
        rule.setFeeValue(newValue);
        rule.setEffectiveFrom(newFrom);
        rule.setEffectiveTo(newTo);
        rule.setTreatmentFeeRuleUpdateAt(Instant.now());
        rule.setTreatmentFeeRuleUpdateBy(actorId);
        return rules.save(rule);
    }

    @Transactional
    public TreatmentFeeRule setEnabled(Long id, boolean enabled, Long actorId) {
        TreatmentFeeRule rule = find(id);
        if (rule.isTreatmentFeeRuleStatus() == enabled) {
            return rule;
        }
        if (enabled) {
            requireNoOverlap(id, rule.getEffectiveFrom(), rule.getEffectiveTo());
        }
        rule.setTreatmentFeeRuleStatus(enabled);
        rule.setTreatmentFeeRuleUpdateAt(Instant.now());
        rule.setTreatmentFeeRuleUpdateBy(actorId);
        return rules.save(rule);
    }

    /** An enabled open-ended rule is ended the day before a newer open-ended one starts; other overlaps are rejected. */
    private void retireOverlapping(Long selfId, LocalDate from, LocalDate to, Long actorId) {
        for (TreatmentFeeRule other : rules.findByTreatmentFeeRuleStatusTrue()) {
            if (!overlaps(other.getEffectiveFrom(), other.getEffectiveTo(), from, to)) {
                continue;
            }
            boolean takesOver = to == null && other.getEffectiveTo() == null && other.getEffectiveFrom().isBefore(from);
            if (!takesOver) {
                throw new IllegalArgumentException("ช่วงวันที่ซ้อนกับกฎค่ามือที่ใช้งานอยู่ (เริ่ม " + other.getEffectiveFrom() + ")");
            }
            if (rules.isUsedAfter(other.getId(), from.minusDays(1))) {
                throw new IllegalStateException("กฎค่ามือที่ใช้งานอยู่ถูกใช้กับการรักษาตั้งแต่วันที่ " + from
                        + " แล้ว จึงสิ้นสุดก่อนวันนั้นไม่ได้");
            }
            other.setEffectiveTo(from.minusDays(1));
            other.setTreatmentFeeRuleUpdateAt(Instant.now());
            other.setTreatmentFeeRuleUpdateBy(actorId);
            rules.save(other);
        }
    }

    private void requireNoOverlap(Long selfId, LocalDate from, LocalDate to) {
        for (TreatmentFeeRule other : rules.findByTreatmentFeeRuleStatusTrue()) {
            if (!other.getId().equals(selfId) && overlaps(other.getEffectiveFrom(), other.getEffectiveTo(), from, to)) {
                throw new IllegalArgumentException("ช่วงวันที่ซ้อนกับกฎค่ามือที่ใช้งานอยู่ (เริ่ม " + other.getEffectiveFrom() + ")");
            }
        }
    }

    private static boolean overlaps(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        return (bTo == null || !aFrom.isAfter(bTo)) && (aTo == null || !bFrom.isAfter(aTo));
    }

    private TreatmentFeeRule find(Long id) {
        return rules.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบกฎค่ามือ"));
    }

    private static void validate(FeeType type, BigDecimal value, LocalDate from, LocalDate to) {
        if (type == null) throw new IllegalArgumentException("ต้องระบุประเภทค่ามือ (Fixed หรือ Percentage)");
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("ค่ามือต้องไม่ติดลบ");
        if (type == FeeType.Percentage && value.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("ค่ามือแบบเปอร์เซ็นต์ต้องอยู่ระหว่าง 0 ถึง 100");
        }
        if (value.stripTrailingZeros().scale() > (type == FeeType.Fixed ? 2 : 4)) {
            throw new IllegalArgumentException(type == FeeType.Fixed ? "ค่ามือแบบคงที่มีทศนิยมได้ไม่เกิน 2 ตำแหน่ง"
                    : "เปอร์เซ็นต์มีทศนิยมได้ไม่เกิน 4 ตำแหน่ง");
        }
        if (value.compareTo(new BigDecimal("99999999.9999")) > 0) throw new IllegalArgumentException("ค่ามือมากเกินไป");
        if (from == null) throw new IllegalArgumentException("ต้องระบุ effective_from");
        if (to != null && to.isBefore(from)) throw new IllegalArgumentException("effective_to ต้องไม่อยู่ก่อน effective_from");
    }
}
