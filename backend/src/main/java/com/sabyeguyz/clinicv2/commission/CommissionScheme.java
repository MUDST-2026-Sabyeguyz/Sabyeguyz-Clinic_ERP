package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.role.Permission;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A versioned commission tier scheme. Per Commission_System_Requirement_Dev,
 * changing a tier range/rate should create a new scheme version rather than
 * editing history, so Course rows frozen against an older version never
 * change retroactively.
 */
@Entity
@Table(name = "commission_scheme")
public class CommissionScheme {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "commission_scheme_id")
    private Long id;

    @Column(name = "scheme_name", length = 100, nullable = false)
    private String schemeName;

    @Column(name = "scheme_version", nullable = false)
    private int schemeVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id")
    private Permission permission;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** NULL = currently active. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "scheme_create_at", nullable = false, updatable = false)
    private Instant schemeCreateAt;

    @Column(name = "scheme_create_by", nullable = false, updatable = false)
    private Long schemeCreateBy;

    @Column(name = "scheme_update_at")
    private Instant schemeUpdateAt;

    @Column(name = "scheme_update_by")
    private Long schemeUpdateBy;

    public CommissionScheme() {}

    public Long getId() { return id; }
    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
    public int getSchemeVersion() { return schemeVersion; }
    public void setSchemeVersion(int schemeVersion) { this.schemeVersion = schemeVersion; }
    public Permission getPermission() { return permission; }
    public void setPermission(Permission permission) { this.permission = permission; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public Instant getSchemeCreateAt() { return schemeCreateAt; }
    public void setSchemeCreateAt(Instant schemeCreateAt) { this.schemeCreateAt = schemeCreateAt; }
    public Long getSchemeCreateBy() { return schemeCreateBy; }
    public void setSchemeCreateBy(Long schemeCreateBy) { this.schemeCreateBy = schemeCreateBy; }
    public Instant getSchemeUpdateAt() { return schemeUpdateAt; }
    public void setSchemeUpdateAt(Instant schemeUpdateAt) { this.schemeUpdateAt = schemeUpdateAt; }
    public Long getSchemeUpdateBy() { return schemeUpdateBy; }
    public void setSchemeUpdateBy(Long schemeUpdateBy) { this.schemeUpdateBy = schemeUpdateBy; }
}
