package com.sabyeguyz.clinicv2.catalog;

import org.springframework.transaction.annotation.Transactional;
import com.sabyeguyz.clinicv2.enums.ServiceType;
import com.sabyeguyz.clinicv2.staff.StaffRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.*;

/** Matches frontend/src/api/client.ts's catalogApi + lookups (read-only). */
@Transactional
@RestController
public class CatalogController {
    private final ServiceRepository services;
    private final StaffRepository staff;

    public CatalogController(ServiceRepository services, StaffRepository staff) {
        this.services = services;
        this.staff = staff;
    }

    public record CourseOption(Long id, String code, String name, BigDecimal price, int sessions,
                                int bonusSessions, int validityDays, boolean active) {}
    public record ServiceOption(Long id, String code, String name, int durationMinutes, BigDecimal price) {}
    public record TherapistOption(Long id, String code, String fullName, String branchCode) {}
    public record LookupService(Long id, String code, String name, int defaultDurationMinutes, boolean active) {}
    public record LookupTherapist(Long id, String code, String fullName, String branchCode, boolean active) {}

    @GetMapping("/api/catalog/courses")
    public List<CourseOption> courses() {
        return services.findByServiceTypeAndServiceStatusTrueOrderByServiceName(ServiceType.Course).stream()
                .map(s -> new CourseOption(s.getId(), s.getServiceCode(), s.getServiceName(), s.getServicePrice(),
                        s.getDefaultTotalVisits() == null ? 0 : s.getDefaultTotalVisits(),
                        s.getDefaultBonusVisits() == null ? 0 : s.getDefaultBonusVisits(),
                        s.getDefaultValidityDays() == null ? 0 : s.getDefaultValidityDays(), true))
                .toList();
    }

    @GetMapping("/api/catalog/services")
    public List<ServiceOption> services() {
        return services.findByServiceStatusTrueOrderByServiceName().stream()
                .filter(s -> s.getServiceType() == ServiceType.SingleVisit)
                .map(s -> new ServiceOption(s.getId(), s.getServiceCode(), s.getServiceName(), 0, s.getServicePrice()))
                .toList();
    }

    @GetMapping("/api/catalog/therapists")
    public List<TherapistOption> therapists() {
        return staff.findAll().stream()
                .filter(s -> s.isStaffStatus())
                .map(s -> new TherapistOption(s.getId(), s.getStaffCode(), s.getStaffFname() + " " + s.getStaffLname(),
                        s.getBranch() == null ? null : s.getBranch().getBranchCode()))
                .toList();
    }

    @GetMapping("/api/lookups/services")
    public List<LookupService> lookupServices() {
        return services.findAll().stream()
                .filter(s -> s.getServiceType() == ServiceType.SingleVisit)
                .map(s -> new LookupService(s.getId(), s.getServiceCode(), s.getServiceName(), 0, s.isServiceStatus()))
                .toList();
    }

    @GetMapping("/api/lookups/therapists")
    public List<LookupTherapist> lookupTherapists(@RequestParam(required = false) String branchCode) {
        return staff.findAll().stream()
                .filter(s -> branchCode == null || branchCode.isBlank()
                        || (s.getBranch() != null && branchCode.equalsIgnoreCase(s.getBranch().getBranchCode())))
                .map(s -> new LookupTherapist(s.getId(), s.getStaffCode(), s.getStaffFname() + " " + s.getStaffLname(),
                        s.getBranch() == null ? null : s.getBranch().getBranchCode(), s.isStaffStatus()))
                .toList();
    }
}
