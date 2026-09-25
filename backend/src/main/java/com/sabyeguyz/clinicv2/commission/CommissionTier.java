package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.role.Permission;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "commission_tier")
public class CommissionTier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tier_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commission_scheme_id")
    private CommissionScheme commissionScheme;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @Column(name = "tier_order", nullable = false)
    private int tierOrder;

    @Column(name = "minimum_monthly_sales", precision = 12, scale = 2, nullable = false)
    private BigDecimal minimumMonthlySales;

    /** NULL = no cap (top tier). */
    @Column(name = "maximum_monthly_sales", precision = 12, scale = 2)
    private BigDecimal maximumMonthlySales;

    @Column(name = "commission_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal commissionRate;

    @Column(name = "tier_status", nullable = false)
    private boolean tierStatus = true;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "tier_create_at", nullable = false, updatable = false)
    private Instant tierCreateAt;

    @Column(name = "tier_create_by", nullable = false, updatable = false)
    private Long tierCreateBy;

    @Column(name = "tier_update_at")
    private Instant tierUpdateAt;

    @Column(name = "tier_update_by")
    private Long tierUpdateBy;

    public CommissionTier() {}

    public Long getId() { return id; }
    public CommissionScheme getCommissionScheme() { return commissionScheme; }
    public void setCommissionScheme(CommissionScheme commissionScheme) { this.commissionScheme = commissionScheme; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
    public int getTierOrder() { return tierOrder; }
    public void setTierOrder(int tierOrder) { this.tierOrder = tierOrder; }
    public BigDecimal getMinimumMonthlySales() { return minimumMonthlySales; }
    public void setMinimumMonthlySales(BigDecimal minimumMonthlySales) { this.minimumMonthlySales = minimumMonthlySales; }
    public BigDecimal getMaximumMonthlySales() { return maximumMonthlySales; }
    public void setMaximumMonthlySales(BigDecimal maximumMonthlySales) { this.maximumMonthlySales = maximumMonthlySales; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public void setCommissionRate(BigDecimal commissionRate) { this.commissionRate = commissionRate; }
    public boolean isTierStatus() { return tierStatus; }
    public void setTierStatus(boolean tierStatus) { this.tierStatus = tierStatus; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public Instant getTierCreateAt() { return tierCreateAt; }
    public void setTierCreateAt(Instant tierCreateAt) { this.tierCreateAt = tierCreateAt; }
    public Long getTierCreateBy() { return tierCreateBy; }
    public void setTierCreateBy(Long tierCreateBy) { this.tierCreateBy = tierCreateBy; }
    public Instant getTierUpdateAt() { return tierUpdateAt; }
    public void setTierUpdateAt(Instant tierUpdateAt) { this.tierUpdateAt = tierUpdateAt; }
    public Long getTierUpdateBy() { return tierUpdateBy; }
    public void setTierUpdateBy(Long tierUpdateBy) { this.tierUpdateBy = tierUpdateBy; }
}
