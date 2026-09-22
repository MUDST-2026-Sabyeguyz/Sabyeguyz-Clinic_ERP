package com.sabyeguyz.clinic.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment-methods")
public class PaymentMethodController {
    private final PaymentMethodRepository repository;

    public PaymentMethodController(PaymentMethodRepository repository) { this.repository = repository; }

    record Request(@NotBlank String code, @NotBlank String name, Boolean active) {}
    record Response(Long id, String code, String name, boolean active) {}

    private Response out(PaymentMethod method) {
        return new Response(method.getId(), method.getCode(), method.getName(), method.isActive());
    }

    @GetMapping
    List<Response> list() { return repository.findAllByOrderByName().stream().map(this::out).toList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Response create(@RequestBody @Valid Request request) {
        String code = request.code().trim().toUpperCase();
        if (repository.findByCode(code).isPresent()) throw new IllegalArgumentException("รหัสช่องทางชำระเงินซ้ำ");
        PaymentMethod method = new PaymentMethod(code, request.name().trim());
        applyActive(method, request.active());
        return out(repository.save(method));
    }

    @PutMapping("/{id}")
    Response update(@PathVariable Long id, @RequestBody @Valid Request request) {
        PaymentMethod method = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบช่องทางชำระเงิน"));
        String code = request.code().trim().toUpperCase();
        if (repository.findByCode(code).filter(found -> !found.getId().equals(id)).isPresent())
            throw new IllegalArgumentException("รหัสช่องทางชำระเงินซ้ำ");
        method.update(code, request.name().trim());
        applyActive(method, request.active());
        return out(repository.save(method));
    }

    @PatchMapping("/{id}/activate")
    Response activate(@PathVariable Long id) {
        PaymentMethod method = find(id);
        method.activate();
        return out(repository.save(method));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable Long id) {
        PaymentMethod method = find(id);
        method.deactivate();
        repository.save(method);
    }

    private PaymentMethod find(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("ไม่พบช่องทางชำระเงิน"));
    }

    private static void applyActive(PaymentMethod method, Boolean active) {
        if (active != null) { if (active) method.activate(); else method.deactivate(); }
    }
}
