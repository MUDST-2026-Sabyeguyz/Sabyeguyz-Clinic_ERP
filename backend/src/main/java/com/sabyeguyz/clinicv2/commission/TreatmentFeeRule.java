package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.catalog.ServiceGroup;
import com.sabyeguyz.clinicv2.enums.CalculationBase;
import com.sabyeguyz.clinicv2.enums.FeeType;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.staff.StaffGroup;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_group_id")
    private StaffGroup staffGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_group_id")
    private ServiceGroup serviceGroup;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "fee_type", nullable = false)
    private FeeType feeType;

    /** THB amount, or percentage depending on feeType. */
    @Column(name = "fee_value", precision = 12, scale = 4, nullable = false)
    private BigDecimal feeValue;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "percentage_base")
    private CalculationBase percentageBase;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "treatment_fee_rule_status", nullable = false)
    private boolean treatmentFeeRuleStatus = true;

    @Column(name = "treatment_fee_rule_create_at", nullable = false, updatable = false)
    private Instant treatmentFeeRuleCreateAt;

    @Column(name = "treatment_fee_rule_create_by", nullable = false, updatable = false)
    private Long treatmentFeeRuleCreateBy;

    @Column(name = "treatment_fee_rule_update_at")
    private Instant treatmentFeeRuleUpdateAt;

    @Column(name = "treatment_fee_rule_update_by")
    private Long treatmentFeeRuleUpdateBy;

    protected TreatmentFeeRule() {}

    public Long getId() { return id; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
    public Staff getStaff() { return staff; }
    public void setStaff(Staff staff) { this.staff = staff; }
    public StaffGroup getStaffGroup() { return staffGroup; }
    public void setStaffGroup(StaffGroup staffGroup) { this.staffGroup = staffGroup; }
    public Service getService() { return service; }
    public void setService(Service service) { this.service = service; }
    public ServiceGroup getServiceGroup() { return serviceGroup; }
    public void setServiceGroup(ServiceGroup serviceGroup) { this.serviceGroup = serviceGroup; }
    public FeeType getFeeType() { return feeType; }
    public void setFeeType(FeeType feeType) { this.feeType = feeType; }
    public BigDecimal getFeeValue() { return feeValue; }
    public void setFeeValue(BigDecimal feeValue) { this.feeValue = feeValue; }
    public CalculationBase getPercentageBase() { return percentageBase; }
    public void setPercentageBase(CalculationBase percentageBase) { this.percentageBase = percentageBase; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public boolean isTreatmentFeeRuleStatus() { return treatmentFeeRuleStatus; }
    public void setTreatmentFeeRuleStatus(boolean treatmentFeeRuleStatus) { this.treatmentFeeRuleStatus = treatmentFeeRuleStatus; }
    public Instant getTreatmentFeeRuleCreateAt() { return treatmentFeeRuleCreateAt; }
    public void setTreatmentFeeRuleCreateAt(Instant treatmentFeeRuleCreateAt) { this.treatmentFeeRuleCreateAt = treatmentFeeRuleCreateAt; }
    public Long getTreatmentFeeRuleCreateBy() { return treatmentFeeRuleCreateBy; }
    public void setTreatmentFeeRuleCreateBy(Long treatmentFeeRuleCreateBy) { this.treatmentFeeRuleCreateBy = treatmentFeeRuleCreateBy; }
    public Instant getTreatmentFeeRuleUpdateAt() { return treatmentFeeRuleUpdateAt; }
    public void setTreatmentFeeRuleUpdateAt(Instant treatmentFeeRuleUpdateAt) { this.treatmentFeeRuleUpdateAt = treatmentFeeRuleUpdateAt; }
    public Long getTreatmentFeeRuleUpdateBy() { return treatmentFeeRuleUpdateBy; }
    public void setTreatmentFeeRuleUpdateBy(Long treatmentFeeRuleUpdateBy) { this.treatmentFeeRuleUpdateBy = treatmentFeeRuleUpdateBy; }
}
