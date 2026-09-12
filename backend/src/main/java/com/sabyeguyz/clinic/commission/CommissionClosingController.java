package com.sabyeguyz.clinic.commission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/commission/closings")
public class CommissionClosingController {
    private final CommissionClosingRepository closings;
    private final CommissionService service;

    public CommissionClosingController(CommissionClosingRepository c, CommissionService s) {
        closings = c;
        service = s;
    }

    public record Response(
            Long id,
            LocalDate month,
            Long employeeId,
            String employeeName,
            BigDecimal sales,
            String version,
            BigDecimal rate,
            String status) {}

    @GetMapping
    List<Response> list(@RequestParam LocalDate month) {
        return closings.findByMonthOrderById(month.withDayOfMonth(1)).stream()
                .map(this::out)
                .toList();
    }

    @PostMapping("/{month}")
    @PreAuthorize("hasRole('MANAGER')")
    List<Response> closeMonth(@PathVariable LocalDate month) {
        return service.closeMonth(month).stream().map(this::out).toList();
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('MANAGER')")
    Response close(@PathVariable long id) {
        CommissionClosing x =
                closings.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("ไม่พบรอบคอมมิชชัน"));
        if ("CLOSED".equals(x.getStatus())) throw new IllegalArgumentException("รอบนี้ปิดแล้ว");
        x.close();
        return out(x);
    }

    private Response out(CommissionClosing x) {
        return new Response(
                x.getId(),
                x.getMonth(),
                x.getEmployee().getId(),
                x.getEmployee().getFullName(),
                x.getSales(),
                x.getVersion(),
                x.getLockedRate(),
                x.getStatus());
    }
}
