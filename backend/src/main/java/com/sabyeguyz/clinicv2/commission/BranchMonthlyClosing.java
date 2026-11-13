package com.sabyeguyz.clinicv2.commission;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.BranchClosingStatus;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Explicit closing confirmation for one branch and month. */
@Entity
@Table(name = "branch_monthly_closing")
public class BranchMonthlyClosing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_monthly_closing_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "closing_month", length = 7, nullable = false)
    private String closingMonth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "closing_status",
            nullable = false,
            columnDefinition = "branch_closing_status_enum"
    )
    private BranchClosingStatus closingStatus = BranchClosingStatus.Open;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    public BranchMonthlyClosing() {}

    public Long getId() {return id;}

    public Branch getBranch() {return branch;}
    public void setBranch(Branch branch) {this.branch = branch;}

    public String getClosingMonth() {return closingMonth;}

    public void setClosingMonth(String closingMonth) {this.closingMonth = closingMonth;}
    public BranchClosingStatus getClosingStatus() {return closingStatus;}

    public void setClosingStatus(BranchClosingStatus closingStatus) {this.closingStatus = closingStatus;}

    public Instant getClosedAt() {return closedAt;}
    public void setClosedAt(Instant closedAt) {this.closedAt = closedAt;}

    public Long getClosedBy() {return closedBy;}
    public void setClosedBy(Long closedBy) {this.closedBy = closedBy;}

    public boolean isClosed() {
        return closingStatus == BranchClosingStatus.Closed;
    }
}
