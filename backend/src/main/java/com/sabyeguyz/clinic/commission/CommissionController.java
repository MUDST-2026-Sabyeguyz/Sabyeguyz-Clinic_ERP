package com.sabyeguyz.clinic.commission;

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
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/commission")
@Profile("!database-v2")
public class CommissionController {
    private final CommissionService service;
    public CommissionController(CommissionService service) { this.service = service; }
    public record Tier(@NotNull @DecimalMin("0.00") BigDecimal minimum, @DecimalMin("0.00") BigDecimal maximum,
                       @NotNull @DecimalMin("0.00") BigDecimal rate, int order) {}
    public record SchemeRequest(@NotBlank String name, @NotBlank String version, @NotNull LocalDate effectiveFrom,
                                LocalDate effectiveTo, @NotEmpty List<@Valid Tier> tiers, Boolean active) {}
    public record SchemeResponse(Long id, String name, String version, LocalDate effectiveFrom, LocalDate effectiveTo, boolean active) {}
    public record TierResponse(Long id, BigDecimal minimum, BigDecimal maximum, BigDecimal rate, int order) {}

    @GetMapping("/schemes") List<SchemeResponse> schemes() { return service.listSchemes().stream().map(this::scheme).toList(); }
    @GetMapping("/schemes/{id}/tiers") List<TierResponse> tiers(@PathVariable long id) { return service.tiers(id).stream().map(t -> new TierResponse(t.getId(), t.getMinimumSales(), t.getMaximumSales(), t.getCommissionRate(), t.getTierOrder())).toList(); }
    @PostMapping("/schemes") @PreAuthorize("hasRole('MANAGER')") @ResponseStatus(HttpStatus.CREATED) SchemeResponse create(@Valid @RequestBody SchemeRequest request) {
        return scheme(service.create(request.name(), request.version(), request.effectiveFrom(), request.effectiveTo(), request.tiers().stream().map(t -> new CommissionService.TierInput(t.minimum(), t.maximum(), t.rate(), t.order())).toList()));
    }
    @PutMapping("/schemes/{id}") @PreAuthorize("hasRole('MANAGER')") SchemeResponse update(@PathVariable long id,@Valid @RequestBody SchemeRequest r){CommissionScheme scheme=service.update(id,r.name(),r.version(),r.effectiveFrom(),r.effectiveTo(),r.tiers().stream().map(t->new CommissionService.TierInput(t.minimum(),t.maximum(),t.rate(),t.order())).toList());if(r.active()!=null)scheme=service.setActive(id,r.active());return scheme(scheme);}
    @PatchMapping("/schemes/{id}/activate") @PreAuthorize("hasRole('MANAGER')") SchemeResponse activate(@PathVariable long id){return scheme(service.activate(id));}
    @DeleteMapping("/schemes/{id}") @PreAuthorize("hasRole('MANAGER')") @ResponseStatus(HttpStatus.NO_CONTENT) void deactivate(@PathVariable long id){service.deactivate(id);}
    @GetMapping("/report") List<CommissionService.ReportRow> report(@RequestParam LocalDate from,@RequestParam LocalDate to){return service.report(from,to);}
    @GetMapping("/treatment-report") List<CommissionService.TreatmentReportRow> treatmentReport(@RequestParam LocalDate from,@RequestParam LocalDate to){return service.treatmentReport(from,to);}
    private SchemeResponse scheme(CommissionScheme s) { return new SchemeResponse(s.getId(), s.getName(), s.getVersion(), s.getEffectiveFrom(), s.getEffectiveTo(), s.isActive()); }
}
