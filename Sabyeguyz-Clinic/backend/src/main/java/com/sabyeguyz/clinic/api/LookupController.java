package com.sabyeguyz.clinic.api;

import com.sabyeguyz.clinic.catalog.ServiceItemRepository;
import com.sabyeguyz.clinic.therapist.TherapistRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lookups")
@Transactional(readOnly = true)
public class LookupController {
    private final ServiceItemRepository services;
    private final TherapistRepository therapists;

    public LookupController(ServiceItemRepository services, TherapistRepository therapists) {
        this.services = services;
        this.therapists = therapists;
    }

    public record ServiceOption(Long id, String code, String name, int defaultDurationMinutes, boolean active) {}
    public record TherapistOption(Long id, String code, String fullName, String branchCode, boolean active) {}

    @GetMapping("/services")
    public List<ServiceOption> services() {
        return services.findAll().stream().map(s -> new ServiceOption(s.getId(), s.getCode(),
                s.getName(), s.getDefaultDurationMinutes(), s.isActive()))
                .sorted(Comparator.comparing(ServiceOption::name)).toList();
    }

    @GetMapping("/therapists")
    public List<TherapistOption> therapists(@RequestParam(required = false) String branchCode) {
        return therapists.findAll().stream()
                .filter(t -> branchCode == null || t.getBranch().getCode().equalsIgnoreCase(branchCode.trim()))
                .map(t -> new TherapistOption(t.getId(), t.getCode(), t.getFullName(),
                        t.getBranch().getCode(), t.isActive()))
                .sorted(Comparator.comparing(TherapistOption::fullName)).toList();
    }
}
