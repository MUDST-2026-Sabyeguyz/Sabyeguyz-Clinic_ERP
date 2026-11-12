package com.sabyeguyz.clinicv2.sale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** Prepaid quantity of one single-visit service, sold before a visit is scheduled. */
@Entity
@Table(name = "single_visit_entitlement")
public class SingleVisitEntitlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "single_visit_entitlement_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, unique = true)
    private SalesTransaction transaction;

    @Column(name = "purchased_quantity", nullable = false)
    private int purchasedQuantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    public SingleVisitEntitlement() {}

    public Long getId() { return id; }
    public SalesTransaction getTransaction() { return transaction; }
    public void setTransaction(SalesTransaction transaction) { this.transaction = transaction; }
    public int getPurchasedQuantity() { return purchasedQuantity; }
    public void setPurchasedQuantity(int purchasedQuantity) { this.purchasedQuantity = purchasedQuantity; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}
