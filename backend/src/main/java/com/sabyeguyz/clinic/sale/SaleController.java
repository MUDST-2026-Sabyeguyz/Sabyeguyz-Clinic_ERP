package com.sabyeguyz.clinic.sale;

import com.sabyeguyz.clinic.api.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.RestController;

/**
 * เส้นทางตาม docs/context.md §8 — การขายอยู่ใต้ {@code /api/payments}
 * และการอ่านใบเสร็จอยู่ที่ {@code /api/receipts/{id}}
 */
@RestController
@Profile("!database-v2")
@RequestMapping("/api")
@Tag(name = "Payments & Receipts", description = "Selling, collecting payment, reading and reversing receipts")
public class SaleController {
    private final SaleService service;

    public SaleController(SaleService service) {
        this.service = service;
    }

    @PostMapping("/payments")
    @Operation(summary = "Sell services or courses and take payment",
            description = "Creates one receipt covering every line and every payment on it. Prices come from the "
                    + "service or course master — the caller cannot send a price — and the payment lines must add "
                    + "up to exactly that total. A course line credits its sessions immediately, so the patient can "
                    + "use one on the same visit. The response is the full receipt.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Receipt issued and revenue recorded"),
            @ApiResponse(responseCode = "400",
                    description = "Validation failed, an item or payment method is inactive, or the payments do not "
                            + "match the total",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Patient, course, or salesperson not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SaleResponse> create(@Valid @RequestBody SaleRequest request) {
        SaleResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/receipts/" + response.id())).body(response);
    }

    @GetMapping("/payments")
    @Operation(summary = "List receipts",
            description = "Paged list of receipts, newest first, including voided ones. Returns a summary per "
                    + "receipt without the line items and payment rows; fetch `/api/receipts/{id}` for those. "
                    + "`paid` is the money actually kept after refunds, so a voided receipt shows zero.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged results, including an empty page when nothing matches"),
            @ApiResponse(responseCode = "400", description = "Invalid page, size, or branch",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Patient not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SaleSearchResponse list(
            @Parameter(description = "Limit to one patient") @RequestParam(required = false) Long patientId,
            @Parameter(description = "Branch code such as RP") @RequestParam(required = false) String branchCode,
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size from 1 to 100") @RequestParam(defaultValue = "20") int size) {
        return service.search(patientId, branchCode, page, size);
    }

    @PostMapping("/payments/{id}/reverse")
    @Operation(summary = "Reverse a receipt",
            description = "Reverses the effects of the sale instead of deleting anything: every payment gets a "
                    + "matching refund row so the revenue nets to zero, and any course sold on the receipt is "
                    + "cancelled. Sessions the patient already used stay in the course ledger because they really "
                    + "happened. The reason, the time, and the original rows are all kept for audit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receipt reversed and its effects undone"),
            @ApiResponse(responseCode = "400", description = "Reason is missing",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Receipt not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Receipt was already reversed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SaleResponse reverse(
            @Parameter(description = "Sale database identifier", required = true) @PathVariable long id,
            @Valid @RequestBody SaleVoidRequest request) {
        return service.voidSale(id, request);
    }

    @GetMapping("/receipts/{id}")
    @Operation(summary = "Get a receipt",
            description = "Returns the receipt with every line item and every payment or refund recorded against it, "
                    + "oldest first. `paid` is the money actually kept after refunds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receipt returned"),
            @ApiResponse(responseCode = "404", description = "Receipt not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SaleResponse get(
            @Parameter(description = "Sale database identifier", required = true) @PathVariable long id) {
        return service.get(id);
    }
}
