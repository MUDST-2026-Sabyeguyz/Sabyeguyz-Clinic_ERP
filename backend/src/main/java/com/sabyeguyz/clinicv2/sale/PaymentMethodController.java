package com.sabyeguyz.clinicv2.sale;

import org.springframework.transaction.annotation.Transactional;
import com.sabyeguyz.clinicv2.auth.CurrentActor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Matches frontend/src/api/client.ts's paymentMethodApi (/api/payment-methods). */
@Transactional
@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {
    private final PaymentMethodRepository repository;
    private final CurrentActor actor;

    public PaymentMethodController(PaymentMethodRepository repository, CurrentActor actor) {
        this.repository = repository;
        this.actor = actor;
    }

    public record Request(@NotBlank String code, @NotBlank String name) {}
    public record Response(Long id, String code, String name, boolean active) {}

    @GetMapping
    public List<Response> list() {
        return repository.findAll().stream().map(PaymentMethodController::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Request request) {
        PaymentMethod method = new PaymentMethod();
        method.setMethodName(request.name());
        method.setPaymentMethodStatus(true);
        method.setPaymentMethodCreateAt(Instant.now());
        method.setPaymentMethodCreateBy(actor.currentId());
        return toResponse(repository.save(method));
    }

    @PutMapping("/{id}")
    public Response update(@PathVariable Long id, @Valid @RequestBody Request request) {
        PaymentMethod method = find(id);
        method.setMethodName(request.name());
        method.setPaymentMethodUpdateAt(Instant.now());
        method.setPaymentMethodUpdateBy(actor.currentId());
        return toResponse(repository.save(method));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) {
        PaymentMethod method = find(id);
        method.setPaymentMethodStatus(false);
        repository.save(method);
    }

    @PatchMapping("/{id}/activate")
    public Response activate(@PathVariable Long id) {
        PaymentMethod method = find(id);
        method.setPaymentMethodStatus(true);
        return toResponse(repository.save(method));
    }

    private PaymentMethod find(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบช่องทางชำระเงิน"));
    }

    // v2's Payment_method has no "code" column (just method_name) - code here mirrors the name.
    private static Response toResponse(PaymentMethod method) {
        return new Response(method.getId(), method.getMethodName(), method.getMethodName(), method.isPaymentMethodStatus());
    }
}
