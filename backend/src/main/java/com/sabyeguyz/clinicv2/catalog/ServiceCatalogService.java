package com.sabyeguyz.clinicv2.catalog;

import com.sabyeguyz.clinicv2.enums.ServiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service/Course catalog management (Clinic ERP Workflow Brief §2 "Services/Courses"). */
@Service
public class ServiceCatalogService {
    private final ServiceRepository repository;

    public ServiceCatalogService(ServiceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<com.sabyeguyz.clinicv2.catalog.Service> list() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public com.sabyeguyz.clinicv2.catalog.Service get(Long id) {
        return find(id);
    }

    @Transactional
    public com.sabyeguyz.clinicv2.catalog.Service create(String serviceCode, String serviceName, ServiceType type,
                                                           BigDecimal price, Integer defaultTotalVisits,
                                                           Integer defaultBonusVisits, Integer defaultValidityDays,
                                                           Long actorId) {
        String code = requireText(serviceCode, "serviceCode");
        if (repository.findByServiceCode(code).isPresent()) {
            throw new IllegalArgumentException("รหัสบริการซ้ำ");
        }
        if (type == ServiceType.Assessment) {
            throw new IllegalArgumentException("ไม่รองรับบริการประเภท Assessment แล้ว (เหลือ SingleVisit และ Course)");
        }
        if (price == null || price.signum() < 0) {
            throw new IllegalArgumentException("ราคาต้องไม่ติดลบ");
        }
        if (type == ServiceType.Course && (defaultTotalVisits == null || defaultTotalVisits < 1)) {
            throw new IllegalArgumentException("คอร์สต้องระบุจำนวนครั้งเริ่มต้นอย่างน้อย 1");
        }
        com.sabyeguyz.clinicv2.catalog.Service service = new com.sabyeguyz.clinicv2.catalog.Service();
        service.setServiceCode(code);
        service.setServiceName(requireText(serviceName, "serviceName"));
        service.setServiceType(require(type, "serviceType"));
        service.setServicePrice(price);
        service.setServiceStatus(true);
        service.setDefaultTotalVisits(defaultTotalVisits);
        service.setDefaultBonusVisits(defaultBonusVisits);
        service.setDefaultValidityDays(defaultValidityDays);
        service.setServiceCreateAt(Instant.now());
        service.setServiceCreateBy(actorId);
        return repository.save(service);
    }

    @Transactional
    public com.sabyeguyz.clinicv2.catalog.Service deactivate(Long id, Long actorId) {
        com.sabyeguyz.clinicv2.catalog.Service service = find(id);
        service.setServiceStatus(false);
        service.setServiceUpdateAt(Instant.now());
        service.setServiceUpdateBy(actorId);
        return repository.save(service);
    }

    private com.sabyeguyz.clinicv2.catalog.Service find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบบริการ"));
    }

    private static <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " ห้ามว่าง");
        }
        return value.trim();
    }
}
