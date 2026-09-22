package com.sabyeguyz.clinic.databasev2.commission;

import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/commission/treatment-fees")
@Profile("database-v2")
public class DatabaseV2TreatmentFeeRuleController {

    public record Request(
            @NotNull Long employeeId,
            @NotBlank String feeType,
            @NotNull @DecimalMin("0") BigDecimal feeValue,
            String percentageBase,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {}

    public record Response(
            Long id,
            Long employeeId,
            String feeType,
            BigDecimal feeValue,
            String percentageBase,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active
    ) {}

    private final DatabaseV2CommissionRepository repository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2TreatmentFeeRuleController(DatabaseV2CommissionRepository repository, CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.actorProvider = actorProvider;
    }

    @GetMapping
    public List<Response> list() {
        return repository.listFeeRules().stream()
                .map(this::out)
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public Response create(@Valid @RequestBody Request r, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        DatabaseV2CommissionRepository.FeeRuleRecord created = repository.createFeeRule(
                r.employeeId(), r.feeType(), r.feeValue(), r.percentageBase(), r.effectiveFrom(), r.effectiveTo(), actorId
        );
        return out(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public void deactivate(@PathVariable long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        repository.deactivateFeeRule(id, actorId);
    }

    private Response out(DatabaseV2CommissionRepository.FeeRuleRecord x) {
        return new Response(x.id(), x.employeeId(), x.feeType(), x.feeValue(), x.percentageBase(), x.effectiveFrom(), x.effectiveTo(), x.active());
    }
}
