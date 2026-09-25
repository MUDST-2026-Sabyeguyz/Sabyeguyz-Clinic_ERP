package com.sabyeguyz.clinicv2.catalog;

import jakarta.persistence.*;

@Entity
@Table(name = "service_group_member")
@IdClass(ServiceGroupMemberId.class)
public class ServiceGroupMember {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_group_id")
    private ServiceGroup serviceGroup;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    public ServiceGroupMember() {}

    public ServiceGroupMember(ServiceGroup serviceGroup, Service service) {
        this.serviceGroup = serviceGroup;
        this.service = service;
    }

    public ServiceGroup getServiceGroup() { return serviceGroup; }
    public Service getService() { return service; }
}
