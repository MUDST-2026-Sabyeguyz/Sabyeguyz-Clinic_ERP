package com.sabyeguyz.clinic.therapist;

import com.sabyeguyz.clinic.branch.Branch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "therapists")
public class Therapist {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "therapist_id_generator")
    @SequenceGenerator(name = "therapist_id_generator", sequenceName = "therapist_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    private String code;

    @Column(name = "full_name")
    private String fullName;

    private boolean active;

    @Column(name = "created_at")
    private Instant createdAt;

    protected Therapist() {}

    public Therapist(Branch branch, String code, String fullName) {
        this.branch = branch;
        this.code = code;
        this.fullName = fullName;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Branch getBranch() { return branch; }
    public String getCode() { return code; }
    public String getFullName() { return fullName; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public void rename(String value) { fullName = value; }
    public void activate() { active = true; }
    public void deactivate() { active = false; }
}
