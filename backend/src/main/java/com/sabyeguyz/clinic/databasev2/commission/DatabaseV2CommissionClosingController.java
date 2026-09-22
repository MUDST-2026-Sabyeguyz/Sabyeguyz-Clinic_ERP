package com.sabyeguyz.clinic.databasev2.commission;

import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/commission/closings")
@Profile("database-v2")
public class DatabaseV2CommissionClosingController {

    public record Response(
            Long id,
            LocalDate month,
            Long employeeId,
            String employeeName,
            BigDecimal sales,
            String version,
            BigDecimal rate,
            String status
    ) {}

    private final DatabaseV2CommissionRepository repository;
    private final CurrentActorProvider actorProvider;

    public DatabaseV2CommissionClosingController(DatabaseV2CommissionRepository repository, CurrentActorProvider actorProvider) {
        this.repository = repository;
        this.actorProvider = actorProvider;
    }

    @GetMapping
    public List<Response> list(@RequestParam LocalDate month) {
        return repository.findClosings(month.withDayOfMonth(1)).stream()
                .map(this::out)
                .toList();
    }

    @PostMapping("/{month}")
    @PreAuthorize("hasRole('MANAGER')")
    public List<Response> closeMonth(@PathVariable LocalDate month, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        return repository.closeMonth(month, actorId).stream()
                .map(this::out)
                .toList();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('MANAGER')")
    public Response close(@PathVariable long id, Authentication auth) {
        Long actorId = actorProvider.getActorIdOrDefault(auth, 1L);
        return out(repository.closeSingle(id, actorId));
    }

    private Response out(DatabaseV2CommissionRepository.ClosingRecord x) {
        return new Response(x.id(), x.month(), x.employeeId(), x.employeeName(), x.sales(), x.version(), x.rate(), x.status());
    }
}
