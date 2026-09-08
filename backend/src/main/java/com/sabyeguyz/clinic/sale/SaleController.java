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
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Sales & Payments", description = "Receipts, payment collection, and voiding")
public class SaleController {
    private final SaleService service;

    public SaleController(SaleService service) {
        this.service = service;
    }

    @PostMapping("/sales")
    @Operation(summary = "Sell services or courses and take payment",
            description = "Prices every line from the service or course master — the caller cannot send a price — "
                    + "then requires the payment lines to add up to exactly that total. A course line credits its "
                    + "sessions immediately, so the patient can use one on the same visit. Returns the receipt.")
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
        return ResponseEntity.created(URI.create("/api/sales/" + response.id())).body(response);
    }

    @GetMapping("/sales/{id}")
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

    @GetMapping("/patients/{patientId}/sales")
    @Operation(summary = "List a patient's receipts",
            description = "Returns every receipt issued to the patient, newest first, including voided ones.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receipts returned, empty list when the patient has none"),
            @ApiResponse(responseCode = "404", description = "Patient not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<SaleResponse> listByPatient(
            @Parameter(description = "Patient database identifier", required = true) @PathVariable long patientId) {
        return service.listByPatient(patientId);
    }

    @PostMapping("/sales/{id}/void")
    @Operation(summary = "Void a receipt",
            description = "Reverses the effects of the sale instead of deleting anything: every payment gets a matching "
                    + "refund row so the revenue nets to zero, and any course sold on the receipt is cancelled. "
                    + "Sessions the patient already used stay in the course ledger because they really happened. "
                    + "The reason, the time, and the original rows are all kept for audit.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receipt voided and its effects reversed"),
            @ApiResponse(responseCode = "400", description = "Reason is missing",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Receipt not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Receipt was already voided",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SaleResponse voidSale(
            @Parameter(description = "Sale database identifier", required = true) @PathVariable long id,
            @Valid @RequestBody SaleVoidRequest request) {
        return service.voidSale(id, request);
    }
}
