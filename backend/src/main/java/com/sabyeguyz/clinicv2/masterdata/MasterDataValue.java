package com.sabyeguyz.clinicv2.masterdata;

import java.time.Instant;

import jakarta.persistence.*;

@Entity
@Table(name = "master_data_value")
public class MasterDataValue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "value_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "type_id")
    private MasterDataType type;

    @Column(name = "value_name", length = 100, nullable = false)
    private String valueName;

    @Column(name = "value_status", nullable = false)
    private boolean valueStatus = true;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "value_create_at", nullable = false, updatable = false)
    private Instant valueCreateAt;

    @Column(name = "value_create_by", nullable = false, updatable = false)
    private Long valueCreateBy;

    @Column(name = "value_update_at")
    private Instant valueUpdateAt;

    @Column(name = "value_update_by")
    private Long valueUpdateBy;

    public MasterDataValue() {}

    public Long getId() { return id; }

    public MasterDataType getType() { return type; }
    public void setType(MasterDataType type) { this.type = type; }

    public String getValueName() { return valueName; }
    public void setValueName(String valueName) { this.valueName = valueName; }

    public boolean isValueStatus() { return valueStatus; }
    public void setValueStatus(boolean valueStatus) { this.valueStatus = valueStatus; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Instant getValueCreateAt() { return valueCreateAt; }
    public void setValueCreateAt(Instant valueCreateAt) { this.valueCreateAt = valueCreateAt; }

    public Long getValueCreateBy() { return valueCreateBy; }
    public void setValueCreateBy(Long valueCreateBy) { this.valueCreateBy = valueCreateBy; }

    public Instant getValueUpdateAt() { return valueUpdateAt; }
    public void setValueUpdateAt(Instant valueUpdateAt) { this.valueUpdateAt = valueUpdateAt; }

    public Long getValueUpdateBy() { return valueUpdateBy; }
    public void setValueUpdateBy(Long valueUpdateBy) { this.valueUpdateBy = valueUpdateBy; }
}
