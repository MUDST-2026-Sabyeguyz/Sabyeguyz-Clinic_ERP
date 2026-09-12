package com.sabyeguyz.clinic.commission;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "commission_tiers", uniqueConstraints = @UniqueConstraint(columnNames = {"scheme_id", "tier_order"}))
public class CommissionTier {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "commission_tier_gen")
    @SequenceGenerator(name = "commission_tier_gen", sequenceName = "commission_tier_id_seq", allocationSize = 1)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "scheme_id") private CommissionScheme scheme;
    @Column(name = "minimum_sales", nullable = false, precision = 14, scale = 2) private BigDecimal minimumSales;
    @Column(name = "maximum_sales", precision = 14, scale = 2) private BigDecimal maximumSales;
    @Column(name = "commission_rate", nullable = false, precision = 7, scale = 6) private BigDecimal commissionRate;
    @Column(name = "tier_order", nullable = false) private int tierOrder;
    @Column(nullable = false) private boolean active = true;
    protected CommissionTier() {}
    public CommissionTier(CommissionScheme scheme, BigDecimal minimumSales, BigDecimal maximumSales, BigDecimal commissionRate, int tierOrder) {
        this.scheme = scheme; this.minimumSales = minimumSales; this.maximumSales = maximumSales; this.commissionRate = commissionRate; this.tierOrder = tierOrder;
    }
    public Long getId() { return id; }
    public CommissionScheme getScheme() { return scheme; }
    public BigDecimal getMinimumSales() { return minimumSales; }
    public BigDecimal getMaximumSales() { return maximumSales; }
    public BigDecimal getCommissionRate() { return commissionRate; }
    public int getTierOrder() { return tierOrder; }
    public boolean isActive() { return active; }
}
