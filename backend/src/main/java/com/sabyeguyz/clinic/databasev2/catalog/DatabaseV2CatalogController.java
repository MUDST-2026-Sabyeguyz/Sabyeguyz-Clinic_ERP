package com.sabyeguyz.clinic.databasev2.catalog;

import com.sabyeguyz.clinic.databasev2.staff.DatabaseV2StaffRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database-v2")
@RequestMapping("/api/catalog")
public class DatabaseV2CatalogController {

    private final DatabaseV2ServiceRepository services;
    private final DatabaseV2StaffRepository staff;

    public DatabaseV2CatalogController(DatabaseV2ServiceRepository services, DatabaseV2StaffRepository staff) {
        this.services = services;
        this.staff = staff;
    }

    public record ServiceResponse(Long id, String code, String name, int durationMinutes, BigDecimal price) {}
    public record CourseResponse(Long id, String code, String name, BigDecimal price, int sessions,
                                 int bonusSessions, int validityDays, boolean active) {}
    public record TherapistResponse(Long id, String code, String fullName, String branchCode) {}

    @GetMapping("/services")
    public List<ServiceResponse> listServices() {
        return services.findActive().stream()
                .filter(s -> !"Course".equalsIgnoreCase(s.type()))
                .map(s -> new ServiceResponse(s.id(), s.code(), s.name(), s.durationMinutes(), s.price()))
                .toList();
    }

    @GetMapping("/courses")
    public List<CourseResponse> listCourses() {
        return services.findActiveByType("Course").stream()
                .map(s -> new CourseResponse(
                        s.id(), s.code(), s.name(), s.price(),
                        s.totalVisits() != null ? s.totalVisits() : 1,
                        s.bonusVisits() != null ? s.bonusVisits() : 0,
                        s.validityDays() != null ? s.validityDays() : 365,
                        s.active()
                ))
                .toList();
    }

    @GetMapping("/therapists")
    public List<TherapistResponse> listTherapists() {
        return staff.findByPosition("Therapist").stream()
                .filter(DatabaseV2StaffRepository.StaffModel::active)
                .map(t -> new TherapistResponse(t.id(), t.code(), t.fullName(), t.branchCode()))
                .toList();
    }
}
