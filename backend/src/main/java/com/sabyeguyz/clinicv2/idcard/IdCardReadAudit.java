package com.sabyeguyz.clinicv2.idcard;

import jakarta.persistence.*;
import java.time.Instant;

/** Who read a card, when, where and why. It never holds anything read from the card. */
@Entity
@Table(name = "id_card_read_audit")
public class IdCardReadAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    @Column(name = "workstation_id", nullable = false)
    private Long workstationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 10)
    private String purpose;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected IdCardReadAudit() {}

    public IdCardReadAudit(Long workstationId, Long userId, Long branchId, String purpose, String outcome) {
        this.workstationId = workstationId;
        this.userId = userId;
        this.branchId = branchId;
        this.purpose = purpose;
        this.outcome = outcome;
    }

    public Long getId() { return id; }
    public Long getWorkstationId() { return workstationId; }
    public Long getUserId() { return userId; }
    public Long getBranchId() { return branchId; }
    public String getPurpose() { return purpose; }
    public String getOutcome() { return outcome; }
}
