package com.sabyeguyz.clinicv2.idcard;

import java.time.Instant;

import jakarta.persistence.*;

import com.sabyeguyz.clinicv2.branch.Branch;

@Entity
@Table(name = "card_reader_workstation")
public class CardReaderWorkstation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workstation_id")
    private Long id;

    @Column(name = "workstation_name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "pairing_code_hash", length = 64)
    private String pairingCodeHash;

    @Column(name = "pairing_expires_at")
    private Instant pairingExpiresAt;

    @Column(name = "workstation_status", nullable = false, length = 10)
    private String status = "Active";

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private Long createdBy;

    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public String getPairingCodeHash() { return pairingCodeHash; }
    public void setPairingCodeHash(String pairingCodeHash) { this.pairingCodeHash = pairingCodeHash; }

    public Instant getPairingExpiresAt() { return pairingExpiresAt; }
    public void setPairingExpiresAt(Instant pairingExpiresAt) { this.pairingExpiresAt = pairingExpiresAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public Instant getCreatedAt() { return createdAt; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
