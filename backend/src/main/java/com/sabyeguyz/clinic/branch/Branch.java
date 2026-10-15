package com.sabyeguyz.clinic.branch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "branch")
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "branch_id_generator")
    @SequenceGenerator(name = "branch_id_generator", sequenceName = "branch_id_seq", allocationSize = 1)
    @Column(name = "branch_id")
    private Long id;

    @Column(name = "branch_code", length = 2, nullable = false, unique = true)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String branchCode;

    @Column(name = "branch_name", nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String phone;

    @Column(name = "branch_status", nullable = false)
    private boolean active;

    @Column(name = "branch_create_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "branch_create_by", updatable = false)
    private Long createdBy;

    @Column(name = "branch_update_at")
    private Instant updatedAt;

    @Column(name = "branch_update_by")
    private Long updatedBy;

    protected Branch() {}

    public Branch(String branchCode, String name, String address, String phone, Long createdBy) {
        this.branchCode = branchCode;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.active = true;
        this.createdAt = Instant.now();
        this.createdBy = createdBy;
    }

    /** branchCode is intentionally not editable here: patients already carry it in their HN. */
    public void update(String name, String address, String phone, Long updatedBy) {
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    public void deactivate(Long updatedBy) {
        this.active = false;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }

    public Long getId() { return id; }
    public String getBranchCode() { return branchCode; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getUpdatedBy() { return updatedBy; }
}
