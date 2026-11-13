package com.sabyeguyz.clinicv2.idcard;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Staff: start a card read at a counter workstation and poll for its one-time result. */
@RestController
@RequestMapping("/api/id-card/reads")
public class IdCardReadController {
    public record StartRequest(
            Long workstationId,
            String purpose) {}

    private final IdCardService service;

    public IdCardReadController(IdCardService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IdCardService.Started start(@RequestBody StartRequest request) {
        return service.start(request.workstationId(), request.purpose());
    }

    @GetMapping("/{id}")
    public IdCardService.ReadView poll(@PathVariable String id) {
        return service.poll(id);
    }
}
