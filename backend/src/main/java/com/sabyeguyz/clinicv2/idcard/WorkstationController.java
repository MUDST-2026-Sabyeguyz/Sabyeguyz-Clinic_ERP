package com.sabyeguyz.clinicv2.idcard;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.sabyeguyz.clinicv2.auth.BranchAccessPolicy;
import com.sabyeguyz.clinicv2.branch.Branch;

/** Manager: add, re-pair and revoke counter card readers. Everyone signed in: the readers of their own branch. */
@RestController
@RequestMapping("/api/id-card/workstations")
public class WorkstationController {
    public record CreateRequest(
            String name,
            String branchCode) {}

    public record WorkstationView(
            Long id,
            String name,
            String branchCode,
            boolean online,
            String status) {}

    private final WorkstationService service;
    private final CardReaderWorkstationRepository workstations;
    private final BranchAccessPolicy branchAccess;

    public WorkstationController(
            WorkstationService service,
            CardReaderWorkstationRepository workstations,
            BranchAccessPolicy branchAccess) {
        this.service = service;
        this.workstations = workstations;
        this.branchAccess = branchAccess;
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public WorkstationService.Created create(@RequestBody CreateRequest request) {
        return service.create(request.name(), request.branchCode());
    }

    @PostMapping("/{id}/pairing-code")
    @PreAuthorize("hasRole('MANAGER')")
    public WorkstationService.Created newPairingCode(@PathVariable Long id) {
        return service.newPairingCode(id);
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasRole('MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id) {
        service.revoke(id);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<WorkstationView> list(@RequestParam(required = false) String branchCode) {
        Branch scope = branchAccess.scope(branchCode);
        boolean manager = branchAccess.isManager();
        List<CardReaderWorkstation> rows = scope == null
                ? workstations.findAllByOrderByNameAsc()
                : workstations.findByBranchIdOrderByNameAsc(scope.getId());

        return rows.stream()
                .filter(w -> manager || "Active".equals(w.getStatus()))
                .map(w -> new WorkstationView(
                        w.getId(),
                        w.getName(),
                        w.getBranch().getBranchCode(),
                        service.isOnline(w),
                        w.getStatus()))
                .toList();
    }
}
