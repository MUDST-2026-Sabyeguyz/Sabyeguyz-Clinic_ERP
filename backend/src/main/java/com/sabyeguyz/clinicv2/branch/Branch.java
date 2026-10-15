package com.sabyeguyz.clinicv2.branch;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Matches the confirmed Branch data dictionary. */
@Entity
@Table(name = "branch")
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    private Long id;

    /** 2-digit numeric code, e.g. "01"-"99". */
    @Column(name = "branch_code", length = 2, nullable = false, unique = true)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String branchCode;

    @Column(name = "branch_name", length = 100, nullable = false)
    private String branchName;

    @Column(length = 255, nullable = false)
    private String address;

    @Column(length = 20, nullable = false)
    private String phone;

    @Column(name = "branch_status", nullable = false)
    private boolean branchStatus = true;

    @Column(name = "branch_create_at", nullable = false, updatable = false)
    private Instant branchCreateAt;

    @Column(name = "branch_create_by", nullable = false, updatable = false)
    private Long branchCreateBy;

    @Column(name = "branch_update_at")
    private Instant branchUpdateAt;

    @Column(name = "branch_update_by")
    private Long branchUpdateBy;

    /** Id of this branch's Google Calendar (one calendar per branch); null = no calendar sync for the branch. */
    @Column(name = "google_calendar_id", length = 255, unique = true)
    private String googleCalendarId;

    public Branch() {}

    public String getGoogleCalendarId() { return googleCalendarId; }
    public void setGoogleCalendarId(String googleCalendarId) { this.googleCalendarId = googleCalendarId; }

    public Long getId() { return id; }
    public String getBranchCode() { return branchCode; }
    public void setBranchCode(String branchCode) { this.branchCode = branchCode; }
    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isBranchStatus() { return branchStatus; }
    public void setBranchStatus(boolean branchStatus) { this.branchStatus = branchStatus; }
    public Instant getBranchCreateAt() { return branchCreateAt; }
    public void setBranchCreateAt(Instant branchCreateAt) { this.branchCreateAt = branchCreateAt; }
    public Long getBranchCreateBy() { return branchCreateBy; }
    public void setBranchCreateBy(Long branchCreateBy) { this.branchCreateBy = branchCreateBy; }
    public Instant getBranchUpdateAt() { return branchUpdateAt; }
    public void setBranchUpdateAt(Instant branchUpdateAt) { this.branchUpdateAt = branchUpdateAt; }
    public Long getBranchUpdateBy() { return branchUpdateBy; }
    public void setBranchUpdateBy(Long branchUpdateBy) { this.branchUpdateBy = branchUpdateBy; }
}
