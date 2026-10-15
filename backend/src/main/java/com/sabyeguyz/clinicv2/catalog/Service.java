package com.sabyeguyz.clinicv2.catalog;

import com.sabyeguyz.clinicv2.enums.ServiceType;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "service")
public class Service {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "service_type", nullable = false, columnDefinition = "service_type_enum")
    private ServiceType serviceType;

    @Column(name = "service_name", length = 150, nullable = false)
    private String serviceName;

    @Column(name = "service_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal servicePrice;

    @Column(name = "service_status", nullable = false)
    private boolean serviceStatus = true;

    @Column(name = "service_code", length = 20, nullable = false, unique = true)
    private String serviceCode;

    @Column(name = "service_create_at", nullable = false, updatable = false)
    private Instant serviceCreateAt;

    @Column(name = "service_create_by", nullable = false, updatable = false)
    private Long serviceCreateBy;

    @Column(name = "service_update_at")
    private Instant serviceUpdateAt;

    @Column(name = "service_update_by")
    private Long serviceUpdateBy;

    /** Template value used when this Service (a Course) is sold; NULL for Assessment/SingleVisit. */
    @Column(name = "default_total_visits")
    private Integer defaultTotalVisits;

    @Column(name = "default_bonus_visits")
    private Integer defaultBonusVisits;

    @Column(name = "default_validity_value")
    private Integer defaultValidityValue;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "default_validity_unit", columnDefinition = "validity_unit_enum")
    private com.sabyeguyz.clinicv2.enums.ValidityUnit defaultValidityUnit;

    @Column(name = "unit_name", nullable = false, length = 30)
    private String unitName = "ครั้ง";

    /** Length of an appointment for this service; null = use the branch default. */
    @Column(name = "default_appointment_minutes")
    private Integer defaultAppointmentMinutes;

    public Service() {}

    public Integer getDefaultAppointmentMinutes() { return defaultAppointmentMinutes; }
    public void setDefaultAppointmentMinutes(Integer minutes) { this.defaultAppointmentMinutes = minutes; }

    public Long getId() { return id; }
    public ServiceType getServiceType() { return serviceType; }
    public void setServiceType(ServiceType serviceType) { this.serviceType = serviceType; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public BigDecimal getServicePrice() { return servicePrice; }
    public void setServicePrice(BigDecimal servicePrice) { this.servicePrice = servicePrice; }
    public boolean isServiceStatus() { return serviceStatus; }
    public void setServiceStatus(boolean serviceStatus) { this.serviceStatus = serviceStatus; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public Instant getServiceCreateAt() { return serviceCreateAt; }
    public void setServiceCreateAt(Instant serviceCreateAt) { this.serviceCreateAt = serviceCreateAt; }
    public Long getServiceCreateBy() { return serviceCreateBy; }
    public void setServiceCreateBy(Long serviceCreateBy) { this.serviceCreateBy = serviceCreateBy; }
    public Instant getServiceUpdateAt() { return serviceUpdateAt; }
    public void setServiceUpdateAt(Instant serviceUpdateAt) { this.serviceUpdateAt = serviceUpdateAt; }
    public Long getServiceUpdateBy() { return serviceUpdateBy; }
    public void setServiceUpdateBy(Long serviceUpdateBy) { this.serviceUpdateBy = serviceUpdateBy; }
    public Integer getDefaultTotalVisits() { return defaultTotalVisits; }
    public void setDefaultTotalVisits(Integer defaultTotalVisits) { this.defaultTotalVisits = defaultTotalVisits; }
    public Integer getDefaultBonusVisits() { return defaultBonusVisits; }
    public void setDefaultBonusVisits(Integer defaultBonusVisits) { this.defaultBonusVisits = defaultBonusVisits; }
    public Integer getDefaultValidityValue() { return defaultValidityValue; }
    public void setDefaultValidityValue(Integer value) { this.defaultValidityValue = value; }
    public com.sabyeguyz.clinicv2.enums.ValidityUnit getDefaultValidityUnit() { return defaultValidityUnit; }
    public void setDefaultValidityUnit(com.sabyeguyz.clinicv2.enums.ValidityUnit unit) { this.defaultValidityUnit = unit; }
    public String getUnitName() { return unitName; }
    public void setUnitName(String value) { this.unitName = value; }
    /** Compatibility for API clients that specify days; months/years keep their calendar semantics. */
    public Integer getDefaultValidityDays() {
        return defaultValidityUnit == com.sabyeguyz.clinicv2.enums.ValidityUnit.Day ? defaultValidityValue : null;
    }
    public void setDefaultValidityDays(Integer days) {
        defaultValidityValue = days;
        defaultValidityUnit = days == null ? null : com.sabyeguyz.clinicv2.enums.ValidityUnit.Day;
    }
    public java.time.LocalDate expiryFrom(java.time.LocalDate date) {
        if (defaultValidityValue == null || defaultValidityValue < 1 || defaultValidityUnit == null)
            throw new IllegalArgumentException("คอร์สต้องระบุระยะเวลาใช้งานอย่างน้อย 1");
        return switch (defaultValidityUnit) {
            case Day -> date.plusDays(defaultValidityValue);
            case Month -> date.plusMonths(defaultValidityValue);
            case Year -> date.plusYears(defaultValidityValue);
        };
    }
}
