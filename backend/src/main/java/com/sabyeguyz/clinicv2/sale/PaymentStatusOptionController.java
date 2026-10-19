package com.sabyeguyz.clinicv2.sale;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Transactional
@RestController
@RequestMapping("/api/payment-statuses")
public class PaymentStatusOptionController {
    private final PaymentStatusOptionRepository repository;

    public PaymentStatusOptionController(PaymentStatusOptionRepository repository) {
        this.repository = repository;
    }

    public record Response(String code, String label, boolean enabled) {}
    public record UpdateRequest(boolean enabled) {}

    @GetMapping
    public List<Response> list() {
        return repository.findAll().stream().map(PaymentStatusOptionController::toResponse).toList();
    }

    @PatchMapping("/{code}")
    @PreAuthorize("hasRole('MANAGER')")
    public Response update(@PathVariable String code, @RequestBody UpdateRequest request) {
        PaymentStatusOption option = repository.findById(code.toUpperCase(java.util.Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสถานะการชำระเงิน"));
        option.setEnabled(request.enabled());
        return toResponse(repository.save(option));
    }

    private static Response toResponse(PaymentStatusOption option) {
        return new Response(option.getCode(), option.getLabel(), option.isEnabled());
    }
}
