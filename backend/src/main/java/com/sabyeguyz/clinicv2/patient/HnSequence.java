package com.sabyeguyz.clinicv2.patient;

import com.sabyeguyz.clinicv2.branch.Branch;
import jakarta.persistence.*;
import java.time.Instant;

/** year_month format: YYMM (per data dictionary format column). */
@Entity
@Table(name = "hn_sequence")
@IdClass(HnSequenceId.class)
public class HnSequence {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Id
    @Column(name = "year_month", length = 4, nullable = false)
    private String yearMonth;

    @Column(name = "last_running_no", nullable = false)
    private int lastRunningNo;

    @Column(name = "last_running_at", nullable = false)
    private Instant lastRunningAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public HnSequence() {}

    public HnSequence(Branch branch, String yearMonth, int lastRunningNo, Instant lastRunningAt, Instant createdAt) {
        this.branch = branch;
        this.yearMonth = yearMonth;
        this.lastRunningNo = lastRunningNo;
        this.lastRunningAt = lastRunningAt;
        this.createdAt = createdAt;
    }

    public Branch getBranch() { return branch; }
    public String getYearMonth() { return yearMonth; }
    public int getLastRunningNo() { return lastRunningNo; }
    public void setLastRunningNo(int lastRunningNo) { this.lastRunningNo = lastRunningNo; }
    public Instant getLastRunningAt() { return lastRunningAt; }
    public void setLastRunningAt(Instant lastRunningAt) { this.lastRunningAt = lastRunningAt; }
    public Instant getCreatedAt() { return createdAt; }
}
