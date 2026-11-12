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

    private com.sabyeguyz.clinicv2.audit.AuditLogService audit;

    public TreatmentFeeRuleService(TreatmentFeeRuleRepository rules) {
        this.rules = rules;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAudit(com.sabyeguyz.clinicv2.audit.AuditLogService audit) {
        this.audit = audit;
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
        requireNoOtherPending(effectiveFrom);
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

    /**
     * Edits a rule. A rule in use may only end earlier (or later), never change what it pays. When a rule that has not
     * started moves to another month, the rule before it follows (it ends the day before the new start).
     */
    @Transactional
    public TreatmentFeeRule update(Long id, FeeType feeType, BigDecimal feeValue, LocalDate effectiveFrom,
                                    LocalDate effectiveTo, Long actorId) {
        TreatmentFeeRule rule = find(id);
        FeeType newType = feeType != null ? feeType : rule.getFeeType();
        BigDecimal newValue = feeValue != null ? feeValue : rule.getFeeValue();
        LocalDate newFrom = effectiveFrom != null ? effectiveFrom : rule.getEffectiveFrom();
        // a version that waits to start is edited in its own month: the month is kept, so is its start date
        if (rule.getEffectiveFrom().isAfter(com.sabyeguyz.clinicv2.config.ClinicTime.today())
                && java.time.YearMonth.from(newFrom).equals(java.time.YearMonth.from(rule.getEffectiveFrom()))) {
            newFrom = rule.getEffectiveFrom();
        }
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
        TreatmentFeeRule predecessor = null;
        LocalDate oldFrom = rule.getEffectiveFrom();
        if (!newFrom.equals(oldFrom) && oldFrom.isAfter(com.sabyeguyz.clinicv2.config.ClinicTime.today())) {
            requireFutureCycleStart(newFrom);
            predecessor = rules.findAllByOrderByEffectiveFromDesc().stream()
                    .filter(other -> !other.getId().equals(id) && oldFrom.minusDays(1).equals(other.getEffectiveTo()))
                    .findFirst().orElse(null);
            if (predecessor != null) {
                if (!newFrom.isAfter(predecessor.getEffectiveFrom())) {
                    throw new IllegalArgumentException("เดือนที่เริ่มต้องอยู่หลังกฎก่อนหน้า (เริ่ม " + predecessor.getEffectiveFrom() + ")");
                }
                if (newFrom.isBefore(oldFrom) && rules.isUsedAfter(predecessor.getId(), newFrom.minusDays(1))) {
                    throw new IllegalStateException("กฎก่อนหน้าถูกใช้กับการรักษาตั้งแต่วันที่ " + newFrom
                            + " แล้ว จึงสิ้นสุดก่อนวันนั้นไม่ได้");
                }
            }
        }
        if (rule.isTreatmentFeeRuleStatus()) {
            requireNoOverlap(id, predecessor == null ? null : predecessor.getId(), newFrom, newTo);
        }
        if (predecessor != null) {
            LocalDate previousEnd = predecessor.getEffectiveTo();
            predecessor.setEffectiveTo(newFrom.minusDays(1));
            predecessor.setTreatmentFeeRuleUpdateAt(Instant.now());
            predecessor.setTreatmentFeeRuleUpdateBy(actorId);
            rules.save(predecessor);
            if (audit != null) {
                audit.recordChange("treatment_fee_rule", String.valueOf(predecessor.getId()), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                        com.sabyeguyz.clinicv2.audit.AuditLogService.fields("effective_to", previousEnd),
                        com.sabyeguyz.clinicv2.audit.AuditLogService.fields("effective_to", predecessor.getEffectiveTo(),
                                "reason", "the next rule now starts " + newFrom), actorId);
            }
        }
        rule.setFeeType(newType);
        rule.setFeeValue(newValue);
        rule.setEffectiveFrom(newFrom);
        rule.setEffectiveTo(newTo);
        rule.setTreatmentFeeRuleUpdateAt(Instant.now());
        rule.setTreatmentFeeRuleUpdateBy(actorId);
        return rules.save(rule);
    }

    /** The name is a label only: it can change at any time, even on a rule already used. */
    @Transactional
    public TreatmentFeeRule rename(Long id, String name, Long actorId) {
        TreatmentFeeRule rule = find(id);
        String clean = name == null || name.isBlank() ? null : name.trim();
        if (clean != null && clean.length() > 100) {
            throw new IllegalArgumentException("ชื่อกฎค่ามือยาวเกิน 100 ตัวอักษร");
        }
        rule.setRuleName(clean);
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
            requireNoOverlap(id, null, rule.getEffectiveFrom(), rule.getEffectiveTo());
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
            // the rule in force is replaced from a later cycle, the 1st of a month after the current one
            requireFutureCycleStart(from);
            if (rules.isUsedAfter(other.getId(), from.minusDays(1))) {
                throw new IllegalStateException("กฎค่ามือที่ใช้งานอยู่ถูกใช้กับการรักษาตั้งแต่วันที่ " + from
                        + " แล้ว จึงสิ้นสุดก่อนวันนั้นไม่ได้");
            }
            LocalDate previousEnd = other.getEffectiveTo();
            other.setEffectiveTo(from.minusDays(1));
            other.setTreatmentFeeRuleUpdateAt(Instant.now());
            other.setTreatmentFeeRuleUpdateBy(actorId);
            rules.save(other);
            if (audit != null) {
                audit.recordChange("treatment_fee_rule", String.valueOf(other.getId()), com.sabyeguyz.clinicv2.enums.AuditAction.UPDATE,
                        com.sabyeguyz.clinicv2.audit.AuditLogService.fields("effective_to", previousEnd),
                        com.sabyeguyz.clinicv2.audit.AuditLogService.fields("effective_to", other.getEffectiveTo(),
                                "reason", "ended by a newer rule starting " + from), actorId);
            }
        }
    }

    /** A rule starts on the 1st of a month after the current one: the pay rules never change in the middle of a month. */
    private static void requireFutureCycleStart(LocalDate from) {
        if (from.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("กฎค่ามือใหม่ต้องเริ่มวันที่ 1 ของเดือน เพื่อให้ใช้ทั้งรอบ");
        }
        java.time.YearMonth current = java.time.YearMonth.from(com.sabyeguyz.clinicv2.config.ClinicTime.today());
        if (!java.time.YearMonth.from(from).isAfter(current)) {
            throw new IllegalArgumentException("กฎค่ามือใหม่ใช้ตั้งแต่รอบถัดไป ต้องเริ่มหลังเดือน " + current);
        }
    }

    /** Only one rule waits to start: a change while one is waiting is made on that rule. */
    private void requireNoOtherPending(LocalDate from) {
        LocalDate today = com.sabyeguyz.clinicv2.config.ClinicTime.today();
        if (from == null || !from.isAfter(today)) return;
        if (rules.findAllByOrderByEffectiveFromDesc().stream().anyMatch(rule -> rule.getEffectiveFrom().isAfter(today))) {
            throw new IllegalStateException("มีกฎค่ามือที่รอเริ่มใช้อยู่แล้ว ให้แก้กฎที่รอนั้นแทนการเพิ่มใหม่");
        }
    }

    private void requireNoOverlap(Long selfId, Long alsoIgnoreId, LocalDate from, LocalDate to) {
        for (TreatmentFeeRule other : rules.findByTreatmentFeeRuleStatusTrue()) {
            if (other.getId().equals(alsoIgnoreId)) continue;
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
