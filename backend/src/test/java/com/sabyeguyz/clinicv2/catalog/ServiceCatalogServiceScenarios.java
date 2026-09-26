package com.sabyeguyz.clinicv2.catalog;

import java.util.Map;
import java.util.LinkedHashMap;
import com.sabyeguyz.clinic.cypress.V2RepositoryStub;

import com.sabyeguyz.clinicv2.enums.ServiceType;
import java.math.BigDecimal;
import java.util.Optional;

public class ServiceCatalogServiceScenarios {
    V2RepositoryStub<ServiceRepository> repositoryStub = new V2RepositoryStub<>(ServiceRepository.class);
    ServiceRepository repository = repositoryStub.proxy();

    public Map<String, Object> createsASingleVisitService() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findByServiceCode", args -> Optional.empty());

        Service service = new ServiceCatalogService(repository).create(
                "PHYSIO_30", "กายภาพบำบัด 30 นาที", ServiceType.SingleVisit, new BigDecimal("700.00"),
                null, null, null, 1L);

        out.put("serviceCode", service.getServiceCode());
        out.put("serviceStatus", service.isServiceStatus());
        return out;
    }

    public Map<String, Object> courseRequiresADefaultTotalVisits() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findByServiceCode", args -> Optional.empty());
        out.putAll(V2RepositoryStub.outcome(() -> { new ServiceCatalogService(repository).create(
                "COURSE_10", "คอร์ส 10 ครั้ง", ServiceType.Course, new BigDecimal("10000.00"),
                null, null, 90, 1L); return null; }));
        return out;
    }

    public Map<String, Object> rejectsNegativePrice() {
        Map<String, Object> out = new LinkedHashMap<>();
        repositoryStub.on("findByServiceCode", args -> Optional.empty());
        out.putAll(V2RepositoryStub.outcome(() -> { new ServiceCatalogService(repository).create(
                "X", "ชื่อ", ServiceType.Assessment, new BigDecimal("-1"), null, null, null, 1L); return null; }));
        return out;
    }
}
