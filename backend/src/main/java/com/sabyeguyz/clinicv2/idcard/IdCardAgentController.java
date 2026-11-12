package com.sabyeguyz.clinicv2.idcard;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** The counter agent: pairs with a one-time code, waits for read commands (long-poll) and returns each result. */
@RestController
@RequestMapping("/api/id-card/agent")
public class IdCardAgentController {
    public record PairRequest(String code) {}

    public record ProgressRequest(
            String requestId,
            String stage) {}

    private final WorkstationService workstations;
    private final IdCardService service;

    public IdCardAgentController(
            WorkstationService workstations,
            IdCardService service) {
        this.workstations = workstations;
        this.service = service;
    }

    @PostMapping("/pair")
    public WorkstationService.Paired pair(@RequestBody PairRequest request) {
        return workstations.pair(request.code());
    }

    @GetMapping("/next")
    public ResponseEntity<IdCardService.AgentCommand> next(
            @RequestHeader(value = "X-Agent-Token", required = false) String agentToken,
            @RequestParam(defaultValue = "25") int waitSeconds) {
        CardReaderWorkstation workstation = workstations.authenticate(agentToken);

        return service.next(workstation, waitSeconds)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/progress")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void progress(
            @RequestHeader(value = "X-Agent-Token", required = false) String agentToken,
            @RequestBody ProgressRequest request) {
        service.progress(workstations.authenticate(agentToken), request.requestId(), request.stage());
    }

    @PostMapping("/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void result(
            @RequestHeader(value = "X-Agent-Token", required = false) String agentToken,
            @RequestBody IdCardService.AgentResult result) {
        service.agentResult(workstations.authenticate(agentToken), result);
    }
}
