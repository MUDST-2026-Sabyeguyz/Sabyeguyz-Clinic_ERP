package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.staff.Staff;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Once closingStatus = true (Closed), the locked rate must not change except via an Admin Override + audit row. */
@Entity
@Table(name = "monthly_commission_closing")
public class MonthlyCommissionClosing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "closing_id")
    private Long id;

    /** yyyy-mm */
    @Column(name = "closing_month", length = 7, nullable = false)
    private String closingMonth;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    @Column(name = "monthly_course_sales", precision = 12, scale = 2, nullable = false)
    private BigDecimal monthlyCourseSales;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commission_scheme_id")
    private CommissionScheme commissionScheme;

    @Column(name = "commission_scheme_version", nullable = false)
    private int commissionSchemeVersion;

    @Column(name = "calculated_commission_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal calculatedCommissionRate;

    @Column(name = "locked_commission_rate", precision = 5, scale = 4)
    private BigDecimal lockedCommissionRate;

    /** false = Open, true = Closed. */
    @Column(name = "closing_status", nullable = false)
    private boolean closingStatus = false;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    protected MonthlyCommissionClosing() {}

    public Long getId() { return id; }
    public String getClosingMonth() { return closingMonth; }
    public void setClosingMonth(String closingMonth) { this.closingMonth = closingMonth; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
    public Staff getStaff() { return staff; }
    public void setStaff(Staff staff) { this.staff = staff; }
    public BigDecimal getMonthlyCourseSales() { return monthlyCourseSales; }
    public void setMonthlyCourseSales(BigDecimal monthlyCourseSales) { this.monthlyCourseSales = monthlyCourseSales; }
    public CommissionScheme getCommissionScheme() { return commissionScheme; }
    public void setCommissionScheme(CommissionScheme commissionScheme) { this.commissionScheme = commissionScheme; }
    public int getCommissionSchemeVersion() { return commissionSchemeVersion; }
    public void setCommissionSchemeVersion(int commissionSchemeVersion) { this.commissionSchemeVersion = commissionSchemeVersion; }
    public BigDecimal getCalculatedCommissionRate() { return calculatedCommissionRate; }
    public void setCalculatedCommissionRate(BigDecimal calculatedCommissionRate) { this.calculatedCommissionRate = calculatedCommissionRate; }
    public BigDecimal getLockedCommissionRate() { return lockedCommissionRate; }
    public void setLockedCommissionRate(BigDecimal lockedCommissionRate) { this.lockedCommissionRate = lockedCommissionRate; }
    public boolean isClosingStatus() { return closingStatus; }
    public void setClosingStatus(boolean closingStatus) { this.closingStatus = closingStatus; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public Long getClosedBy() { return closedBy; }
    public void setClosedBy(Long closedBy) { this.closedBy = closedBy; }
}
