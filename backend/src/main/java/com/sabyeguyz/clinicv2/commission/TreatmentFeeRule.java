package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.role.Permission;

/** Fee paid to the Treating PT when they are not the Course's Case Owner. Never counted as that PT's Commission Tier. */
@Entity
@Table(name = "treatment_fee_rule")
public class TreatmentFeeRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "treatment_fee_rule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "fee_type", nullable = false, columnDefinition = "fee_type_enum")
    private FeeType feeType;

    /** THB amount, or percentage depending on feeType. */
    /** The name the Manager gave the rule on the settings page (optional). */
    @Column(name = "rule_name", length = 100)
    private String ruleName;

    @Column(name = "rule_version", nullable = false, unique = true)
    private Integer ruleVersion;

    @Column(name = "fee_value", precision = 12, scale = 4, nullable = false)
    private BigDecimal feeValue;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "treatment_fee_rule_create_at", nullable = false, updatable = false)
    private Instant treatmentFeeRuleCreateAt;

    @Column(name = "treatment_fee_rule_create_by", nullable = false, updatable = false)
    private Long treatmentFeeRuleCreateBy;

    @Column(name = "treatment_fee_rule_update_at")
    private Instant treatmentFeeRuleUpdateAt;

    @Column(name = "treatment_fee_rule_update_by")
    private Long treatmentFeeRuleUpdateBy;

    public TreatmentFeeRule() {}

    public Long getId() { return id; }

    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }

    public FeeType getFeeType() { return feeType; }
    public void setFeeType(FeeType feeType) { this.feeType = feeType; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public Integer getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(Integer ruleVersion) { this.ruleVersion = ruleVersion; }

    public BigDecimal getFeeValue() { return feeValue; }
    public void setFeeValue(BigDecimal feeValue) { this.feeValue = feeValue; }

    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }

    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public Instant getTreatmentFeeRuleCreateAt() { return treatmentFeeRuleCreateAt; }
    public void setTreatmentFeeRuleCreateAt(Instant treatmentFeeRuleCreateAt) { this.treatmentFeeRuleCreateAt = treatmentFeeRuleCreateAt; }

    public Long getTreatmentFeeRuleCreateBy() { return treatmentFeeRuleCreateBy; }
    public void setTreatmentFeeRuleCreateBy(Long treatmentFeeRuleCreateBy) { this.treatmentFeeRuleCreateBy = treatmentFeeRuleCreateBy; }

    public Instant getTreatmentFeeRuleUpdateAt() { return treatmentFeeRuleUpdateAt; }
    public void setTreatmentFeeRuleUpdateAt(Instant treatmentFeeRuleUpdateAt) { this.treatmentFeeRuleUpdateAt = treatmentFeeRuleUpdateAt; }

    public Long getTreatmentFeeRuleUpdateBy() { return treatmentFeeRuleUpdateBy; }
    public void setTreatmentFeeRuleUpdateBy(Long treatmentFeeRuleUpdateBy) { this.treatmentFeeRuleUpdateBy = treatmentFeeRuleUpdateBy; }

}
