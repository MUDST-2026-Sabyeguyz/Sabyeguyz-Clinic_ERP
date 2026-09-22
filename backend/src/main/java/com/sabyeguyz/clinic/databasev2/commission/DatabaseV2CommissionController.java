package com.sabyeguyz.clinic.databasev2.commission;

import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/commission")
@Profile("database-v2")
public class DatabaseV2CommissionController {

    public record Tier(
            @NotNull @DecimalMin("0.00") BigDecimal minimum,
            @DecimalMin("0.00") BigDecimal maximum,
            @NotNull @DecimalMin("0.00") BigDecimal rate,
            int order
    ) {}

    public record SchemeRequest(
            @NotBlank String name,
            @NotBlank String version,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotEmpty List<@Valid Tier> tiers,
            Boolean active
    ) {}

    public record SchemeResponse(
            Long id,
            String name,
            String version,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active
    ) {}

    public record TierResponse(
            Long id,
            BigDecimal minimum,
            BigDecimal maximum,
            BigDecimal rate,
            int order
    ) {}

    private final DatabaseV2CommissionRepository repository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2CommissionController(DatabaseV2CommissionRepository repository, CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.actorProvider = actorProvider;
    }

    @GetMapping("/schemes")
    public List<SchemeResponse> schemes() {
        return repository.listSchemes().stream()
                .map(s -> new SchemeResponse(s.id(), s.name(), s.version(), s.effectiveFrom(), s.effectiveTo(), s.active()))
                .toList();
    }

    @GetMapping("/schemes/{id}/tiers")
    public List<TierResponse> tiers(@PathVariable long id) {
        return repository.listTiers(id).stream()
                .map(t -> new TierResponse(t.id(), t.minimum(), t.maximum(), t.rate(), t.order()))
                .toList();
    }

    @PostMapping("/schemes")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public SchemeResponse create(@Valid @RequestBody SchemeRequest request, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        List<DatabaseV2CommissionRepository.TierInput> tierInputs = request.tiers().stream()
                .map(t -> new DatabaseV2CommissionRepository.TierInput(t.minimum(), t.maximum(), t.rate(), t.order()))
                .toList();
        DatabaseV2CommissionRepository.SchemeRecord created = repository.createScheme(
                request.name(), request.version(), request.effectiveFrom(), request.effectiveTo(), tierInputs, actorId
        );
        return new SchemeResponse(created.id(), created.name(), created.version(), created.effectiveFrom(), created.effectiveTo(), created.active());
    }

    @PutMapping("/schemes/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public SchemeResponse update(@PathVariable long id, @Valid @RequestBody SchemeRequest r, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        List<DatabaseV2CommissionRepository.TierInput> tierInputs = r.tiers().stream()
                .map(t -> new DatabaseV2CommissionRepository.TierInput(t.minimum(), t.maximum(), t.rate(), t.order()))
                .toList();
        DatabaseV2CommissionRepository.SchemeRecord updated = repository.updateScheme(
                id, r.name(), r.version(), r.effectiveFrom(), r.effectiveTo(), tierInputs, r.active(), actorId
        );
        return new SchemeResponse(updated.id(), updated.name(), updated.version(), updated.effectiveFrom(), updated.effectiveTo(), updated.active());
    }

    @PatchMapping("/schemes/{id}/activate")
    @PreAuthorize("hasRole('MANAGER')")
    public SchemeResponse activate(@PathVariable long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2CommissionRepository.SchemeRecord s = repository.activateScheme(id, actorId);
        return new SchemeResponse(s.id(), s.name(), s.version(), s.effectiveFrom(), s.effectiveTo(), s.active());
    }

    @DeleteMapping("/schemes/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        repository.deactivateScheme(id, actorId);
    }

    @GetMapping("/report")
    public List<DatabaseV2CommissionRepository.ReportRow> report(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return repository.report(from, to);
    }

    @GetMapping("/treatment-report")
    public List<DatabaseV2CommissionRepository.TreatmentReportRow> treatmentReport(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return repository.treatmentReport(from, to);
    }
}
