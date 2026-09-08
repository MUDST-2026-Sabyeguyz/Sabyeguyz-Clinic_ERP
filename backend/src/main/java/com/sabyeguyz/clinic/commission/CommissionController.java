package com.sabyeguyz.clinic.commission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/commission")
public class CommissionController {
    private final CommissionService service;
    public CommissionController(CommissionService service) { this.service = service; }
    public record Tier(@NotNull @DecimalMin("0.00") BigDecimal minimum, @DecimalMin("0.00") BigDecimal maximum,
                       @NotNull @DecimalMin("0.00") BigDecimal rate, int order) {}
    public record SchemeRequest(@NotBlank String name, @NotBlank String version, @NotNull LocalDate effectiveFrom,
                                LocalDate effectiveTo, @NotEmpty List<@Valid Tier> tiers) {}
    public record SchemeResponse(Long id, String name, String version, LocalDate effectiveFrom, LocalDate effectiveTo, boolean active) {}
    public record TierResponse(Long id, BigDecimal minimum, BigDecimal maximum, BigDecimal rate, int order) {}

    @GetMapping("/schemes") List<SchemeResponse> schemes() { return service.listSchemes().stream().map(this::scheme).toList(); }
    @GetMapping("/schemes/{id}/tiers") List<TierResponse> tiers(@PathVariable long id) { return service.tiers(id).stream().map(t -> new TierResponse(t.getId(), t.getMinimumSales(), t.getMaximumSales(), t.getCommissionRate(), t.getTierOrder())).toList(); }
    @PostMapping("/schemes") @ResponseStatus(HttpStatus.CREATED) SchemeResponse create(@Valid @RequestBody SchemeRequest request) {
        return scheme(service.create(request.name(), request.version(), request.effectiveFrom(), request.effectiveTo(), request.tiers().stream().map(t -> new CommissionService.TierInput(t.minimum(), t.maximum(), t.rate(), t.order())).toList()));
    }
    private SchemeResponse scheme(CommissionScheme s) { return new SchemeResponse(s.getId(), s.getName(), s.getVersion(), s.getEffectiveFrom(), s.getEffectiveTo(), s.isActive()); }
}
