package com.sabyeguyz.clinic.databasev2.catalog;

import com.sabyeguyz.clinic.databasev2.staff.DatabaseV2StaffRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("database-v2")
@RequestMapping("/api/lookups")
public class DatabaseV2LookupController {

    private final DatabaseV2ServiceRepository services;
    private final DatabaseV2StaffRepository staff;

    public DatabaseV2LookupController(DatabaseV2ServiceRepository services, DatabaseV2StaffRepository staff) {
        this.services = services;
        this.staff = staff;
    }

    public record ServiceOption(Long id, String code, String name, int defaultDurationMinutes, boolean active) {}
    public record TherapistOption(Long id, String code, String fullName, String branchCode, boolean active) {}

    @GetMapping("/services")
    public List<ServiceOption> services() {
        return services.findAll().stream()
                .filter(s -> !"Course".equalsIgnoreCase(s.type()))
                .map(s -> new ServiceOption(s.id(), s.code(), s.name(), 60, s.active()))
                .sorted(Comparator.comparing(ServiceOption::name))
                .toList();
    }

    @GetMapping("/therapists")
    public List<TherapistOption> therapists(@RequestParam(required = false) String branchCode) {
        return staff.findByPosition("Therapist").stream()
                .filter(t -> branchCode == null || branchCode.isBlank() || (t.branchCode() != null && t.branchCode().equalsIgnoreCase(branchCode.trim())))
                .map(t -> new TherapistOption(t.id(), t.code(), t.fullName(), t.branchCode(), t.active()))
                .sorted(Comparator.comparing(TherapistOption::fullName))
                .toList();
    }
}
