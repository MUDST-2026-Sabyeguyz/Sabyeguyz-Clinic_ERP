package com.sabyeguyz.clinicv2.appointment;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentProcessController {
    private final AppointmentProcessService processes;
    public AppointmentProcessController(AppointmentProcessService processes) { this.processes = processes; }
    @GetMapping("/{id}/progress")
    public AppointmentProcessService.Progress progress(@PathVariable Long id) { return processes.progress(id); }
    public record SettleRequest(java.math.BigDecimal amount, String paymentMethodCode) {}
    public record ReviewRequest(String reason) {}
    public record ReconcileRequest(Long receiptId, Long usageId, String reason) {}
    @GetMapping("/{id}/document-candidates")
    public AppointmentProcessService.Candidates candidates(@PathVariable Long id) { return processes.candidates(id); }
    @PostMapping("/{id}/link-documents")
    public AppointmentProcessService.Progress reconcile(@PathVariable Long id, @RequestBody ReconcileRequest request) {
        return processes.reconcile(id, request.receiptId(), request.usageId(), request.reason());
    }
    @PostMapping("/{id}/verify-no-documents")
    public AppointmentProcessService.Progress review(@PathVariable Long id, @RequestBody ReviewRequest request) {
        return processes.verifyNoPreviousDocuments(id, request.reason());
    }
    @PostMapping("/{id}/settle")
    public AppointmentProcessService.Progress settle(@PathVariable Long id, @RequestBody SettleRequest request) {
        return processes.settle(id, request.amount(), request.paymentMethodCode());
    }
}
