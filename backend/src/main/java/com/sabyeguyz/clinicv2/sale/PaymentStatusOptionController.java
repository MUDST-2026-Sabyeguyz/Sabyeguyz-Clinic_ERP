package com.sabyeguyz.clinicv2.sale;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sabyeguyz.clinicv2.auth.CurrentActor;

@Transactional
@RestController
@RequestMapping("/api/payment-statuses")
public class PaymentStatusOptionController {
    private final PaymentLabelOptionRepository  repository;
    private final CurrentActor actor;

    public PaymentStatusOptionController(
            PaymentLabelOptionRepository repository,
            CurrentActor actor) {
        this.repository = repository;
        this.actor = actor;
    }

    public record Response(
            String code,
            String label,
            boolean enabled) {}           // ex {"enabled": false}
    public record UpdateRequest(boolean enabled) {}

    @GetMapping
    public List<Response> list() {
        return repository.findAllByOrderByIdAsc().stream()
                .map(PaymentStatusOptionController::toResponse)
                .toList();
    }

    @PatchMapping("/{code}")
    @PreAuthorize("hasRole('MANAGER')")
    public Response update(
            @PathVariable String code,
            @RequestBody UpdateRequest request) {

        String normalized = code.toUpperCase(Locale.ROOT);
        String databaseCode = "PAID".equals(normalized) ? "FULL" : normalized;

        PaymentLabelOption option = repository.findByCode(databaseCode)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสถานะการชำระเงิน"));

        option.setEnabled(request.enabled());
        option.setUpdatedBy(actor.requireId());
        option.setUpdatedAt(Instant.now());

        return toResponse(repository.save(option));
    }

    // Convert data back for the frontend
    private static Response toResponse(PaymentLabelOption option) {
        String apiCode = "FULL".equals(option.getCode())
                ? "PAID" : option.getCode();

        return new Response(
                apiCode,
                option.getName(),
                option.isEnabled());
    }
}
