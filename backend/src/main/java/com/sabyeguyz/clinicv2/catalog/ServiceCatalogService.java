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
        service.setDefaultBonusVisits(0);
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

    @Transactional
    public com.sabyeguyz.clinicv2.catalog.Service activate(Long id, Long actorId) {
        com.sabyeguyz.clinicv2.catalog.Service service = find(id);
        service.setServiceStatus(true);
        service.setServiceUpdateAt(Instant.now());
        service.setServiceUpdateBy(actorId);
        return repository.save(service);
    }

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /**
     * Turns a Single Visit into a Course or back. Only an item nothing refers to yet can change type: once it has been
     * sold, booked or used, its type is part of that history.
     */
    @Transactional
    public com.sabyeguyz.clinicv2.catalog.Service changeType(Long id, ServiceType type, Long actorId) {
        com.sabyeguyz.clinicv2.catalog.Service service = find(id);
        if (type != ServiceType.SingleVisit && type != ServiceType.Course) {
            throw new IllegalArgumentException("ประเภทต้องเป็น Single Visit หรือ Course");
        }
        if (service.getServiceType() == type) return service;
        @SuppressWarnings("unchecked")
        List<Object[]> references = entityManager.createNativeQuery(
                "SELECT kcu.table_name, kcu.column_name FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name "
                        + "AND tc.table_schema = kcu.table_schema "
                        + "JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name "
                        + "AND ccu.table_schema = tc.table_schema "
                        + "WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name = 'service' "
                        + "AND ccu.column_name = 'service_id' AND kcu.table_name <> 'service_group_member'")
                .getResultList();
        for (Object[] reference : references) {
            Object used = entityManager.createNativeQuery("SELECT EXISTS (SELECT 1 FROM " + reference[0]
                    + " WHERE " + reference[1] + " = :id)").setParameter("id", id).getSingleResult();
            if (Boolean.TRUE.equals(used)) {
                throw new IllegalStateException("รายการนี้ถูกใช้งานแล้ว (ขาย นัดหมาย หรือใช้สิทธิ์) จึงเปลี่ยนประเภทไม่ได้ ให้สร้างรายการใหม่");
            }
        }
        service.setServiceType(type);
        if (type == ServiceType.SingleVisit) {
            service.setDefaultTotalVisits(null);
            service.setDefaultBonusVisits(0);
            service.setDefaultValidityDays(null);
            service.setDefaultValidityValue(null);
            service.setDefaultValidityUnit(null);
        }
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
