package com.sabyeguyz.clinicv2.catalog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sabyeguyz.clinicv2.enums.ServiceType;

public interface ServiceRepository extends JpaRepository<Service, Long> {
    Optional<Service> findByServiceCode(String serviceCode);

    List<Service> findByServiceTypeOrderByServiceName(ServiceType serviceType);

    List<Service> findByServiceTypeAndServiceStatusTrueOrderByServiceName(ServiceType serviceType);

    List<Service> findByServiceStatusTrueOrderByServiceName();
}
