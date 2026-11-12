package com.sabyeguyz.clinicv2.sale;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "payment_label")
public class PaymentLabelOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_label_id")
    private Long id;

    @Column(name = "payment_label_code",
            length = 20, nullable = false, unique = true, updatable = false)
    private String code;

    @Column(name = "payment_label_name",
            length = 50, nullable = false, unique = true)
    private String name;

    @Column(name = "payment_label_status", nullable = false)
    private boolean enabled = true;

    @Column(name = "payment_label_create_at",
            nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "payment_label_create_by",
            nullable = false, updatable = false)
    private Long createdBy;

    @Column(name = "payment_label_update_at")
    private Instant updatedAt;

    @Column(name = "payment_label_update_by")
    private Long updatedBy;

    protected PaymentLabelOption() {}

    public Long getId() { return id; }

    public String getCode() { return code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }

    public Long getCreatedBy() { return createdBy; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
}
