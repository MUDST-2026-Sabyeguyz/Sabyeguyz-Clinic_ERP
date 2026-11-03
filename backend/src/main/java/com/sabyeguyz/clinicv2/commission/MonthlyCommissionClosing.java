package com.sabyeguyz.clinicv2.commission;

import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;
import com.sabyeguyz.clinicv2.enums.ClosingStatus;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.staff.Staff;
import com.sabyeguyz.clinicv2.branch.Branch;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Once the closing is Closed or Finalized, the locked rate must not change except via an Admin Override + audit row. */
@Entity
@Table(name = "monthly_commission_closing")
public class MonthlyCommissionClosing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "closing_id")
    private Long id;

    /** yyyy-mm */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "closing_month", length = 7, nullable = false)
    private String closingMonth;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id")
    private Staff staff;

    /** Historical branch of this monthly record. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

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

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "closing_status", nullable = false, columnDefinition = "closing_status_enum")
    private ClosingStatus closingStatus = ClosingStatus.Open;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    /** The latest Reopen of this month: why, by whom and when (the audit log keeps the earlier ones). */
    @Column(name = "reopen_reason", length = 255)
    private String reopenReason;

    @Column(name = "reopened_by")
    private Long reopenedBy;

    @Column(name = "reopened_at")
    private Instant reopenedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "finalized_by")
    private Long finalizedBy;

    public MonthlyCommissionClosing() {}

    public Long getId() { return id; }
    public String getClosingMonth() { return closingMonth; }
    public void setClosingMonth(String closingMonth) { this.closingMonth = closingMonth; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
    public Staff getStaff() { return staff; }
    public void setStaff(Staff staff) { this.staff = staff; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
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
    public ClosingStatus getClosingStatus() { return closingStatus; }
    public void setClosingStatus(ClosingStatus closingStatus) { this.closingStatus = closingStatus; }
    /** True once the month was closed (Closed or Finalized). */
    public boolean isClosed() { return closingStatus != ClosingStatus.Open; }
    public Instant getFinalizedAt() { return finalizedAt; }
    public void setFinalizedAt(Instant finalizedAt) { this.finalizedAt = finalizedAt; }
    public Long getFinalizedBy() { return finalizedBy; }
    public void setFinalizedBy(Long finalizedBy) { this.finalizedBy = finalizedBy; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public String getReopenReason() { return reopenReason; }
    public void setReopenReason(String reopenReason) { this.reopenReason = reopenReason; }
    public Long getReopenedBy() { return reopenedBy; }
    public void setReopenedBy(Long reopenedBy) { this.reopenedBy = reopenedBy; }
    public Instant getReopenedAt() { return reopenedAt; }
    public void setReopenedAt(Instant reopenedAt) { this.reopenedAt = reopenedAt; }
    public Long getClosedBy() { return closedBy; }
    public void setClosedBy(Long closedBy) { this.closedBy = closedBy; }
}
