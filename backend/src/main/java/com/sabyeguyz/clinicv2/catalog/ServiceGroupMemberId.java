package com.sabyeguyz.clinicv2.catalog;

import java.io.Serializable;
import java.util.Objects;

public class ServiceGroupMemberId implements Serializable {
    private Long serviceGroup;
    private Long service;

    public ServiceGroupMemberId() {}

    public ServiceGroupMemberId(Long serviceGroup, Long service) {
        this.serviceGroup = serviceGroup;
        this.service = service;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServiceGroupMemberId that)) return false;
        return Objects.equals(serviceGroup, that.serviceGroup) && Objects.equals(service, that.service);
    }

    @Override
    public int hashCode() { return Objects.hash(serviceGroup, service); }
}
