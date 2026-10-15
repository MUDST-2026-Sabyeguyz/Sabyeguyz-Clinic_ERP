package com.sabyeguyz.clinicv2.catalog;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "service_group")
public class ServiceGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_group_id")
    private Long id;

    /** Used by Treatment Fee Rule. */
    @Column(name = "group_name", length = 100, nullable = false)
    private String groupName;

    @Column(name = "service_group_create_at", nullable = false, updatable = false)
    private Instant serviceGroupCreateAt;

    @Column(name = "service_group_create_by", nullable = false, updatable = false)
    private Long serviceGroupCreateBy;

    @Column(name = "service_group_update_at")
    private Instant serviceGroupUpdateAt;

    @Column(name = "service_group_update_by")
    private Long serviceGroupUpdateBy;

    public ServiceGroup() {}

    public Long getId() { return id; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public Instant getServiceGroupCreateAt() { return serviceGroupCreateAt; }
    public void setServiceGroupCreateAt(Instant serviceGroupCreateAt) { this.serviceGroupCreateAt = serviceGroupCreateAt; }
    public Long getServiceGroupCreateBy() { return serviceGroupCreateBy; }
    public void setServiceGroupCreateBy(Long serviceGroupCreateBy) { this.serviceGroupCreateBy = serviceGroupCreateBy; }
    public Instant getServiceGroupUpdateAt() { return serviceGroupUpdateAt; }
    public void setServiceGroupUpdateAt(Instant serviceGroupUpdateAt) { this.serviceGroupUpdateAt = serviceGroupUpdateAt; }
    public Long getServiceGroupUpdateBy() { return serviceGroupUpdateBy; }
    public void setServiceGroupUpdateBy(Long serviceGroupUpdateBy) { this.serviceGroupUpdateBy = serviceGroupUpdateBy; }
}
