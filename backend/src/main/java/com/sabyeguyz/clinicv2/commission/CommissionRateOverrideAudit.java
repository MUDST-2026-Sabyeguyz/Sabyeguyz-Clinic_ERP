package com.sabyeguyz.clinicv2.commission;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.*;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.role.Permission;

/** Audits calculated rate changes after Reopen and before Finalize. */
@Entity
@Table(name = "commission_rate_override_audit")
public class CommissionRateOverrideAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "override_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closing_id")
    private MonthlyCommissionClosing closing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "old_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal oldRate;

    @Column(name = "new_rate", precision = 5, scale = 4, nullable = false)
    private BigDecimal newRate;

    @Column(name = "changed_by", nullable = false)
    private Long changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(length = 255, nullable = false)
    private String reason;

    public CommissionRateOverrideAudit() {}

    public Long getId() { return id; }

    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }

    public MonthlyCommissionClosing getClosing() { return closing; }
    public void setClosing(MonthlyCommissionClosing closing) { this.closing = closing; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public BigDecimal getOldRate() { return oldRate; }
    public void setOldRate(BigDecimal oldRate) { this.oldRate = oldRate; }

    public BigDecimal getNewRate() { return newRate; }
    public void setNewRate(BigDecimal newRate) { this.newRate = newRate; }

    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
