package com.sabyeguyz.clinicv2.masterdata;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "master_data_type")
public class MasterDataType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "type_id")
    private Long id;

    /** e.g. CUSTOMER_GROUP, REFERRAL_CHANNEL, INSURANCE_COMPANY */
    @Column(name = "type_name", length = 50, nullable = false)
    private String typeName;

    @Column(name = "type_create_at", nullable = false, updatable = false)
    private Instant typeCreateAt;

    @Column(name = "type_create_by", nullable = false, updatable = false)
    private Long typeCreateBy;

    @Column(name = "type_update_at")
    private Instant typeUpdateAt;

    @Column(name = "type_update_by")
    private Long typeUpdateBy;

    public MasterDataType() {}

    public Long getId() { return id; }

    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }

    public Instant getTypeCreateAt() { return typeCreateAt; }
    public void setTypeCreateAt(Instant typeCreateAt) { this.typeCreateAt = typeCreateAt; }

    public Long getTypeCreateBy() { return typeCreateBy; }
    public void setTypeCreateBy(Long typeCreateBy) { this.typeCreateBy = typeCreateBy; }

    public Instant getTypeUpdateAt() { return typeUpdateAt; }
    public void setTypeUpdateAt(Instant typeUpdateAt) { this.typeUpdateAt = typeUpdateAt; }

    public Long getTypeUpdateBy() { return typeUpdateBy; }
    public void setTypeUpdateBy(Long typeUpdateBy) { this.typeUpdateBy = typeUpdateBy; }
}
