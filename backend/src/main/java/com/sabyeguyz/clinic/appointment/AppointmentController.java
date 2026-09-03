package com.sabyeguyz.clinic.appointment;

import com.sabyeguyz.clinic.api.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Parameter;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments", description = "Appointment booking APIs")
public class AppointmentController {
    private final AppointmentBookingService bookingService;
    private final AppointmentQueryService queryService;
    private final AppointmentLifecycleService lifecycleService;

    public AppointmentController(AppointmentBookingService bookingService, AppointmentQueryService queryService,
                                 AppointmentLifecycleService lifecycleService) {
        this.bookingService = bookingService;
        this.queryService = queryService;
        this.lifecycleService = lifecycleService;
    }

    @PostMapping
    @Operation(summary = "Create appointment",
            description = "Books an appointment for the selected therapist and rejects overlapping time slots.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Appointment created with status SCHEDULED"),
            @ApiResponse(responseCode = "400", description = "Request validation failed or referenced data is invalid",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Therapist already has an appointment in the selected period",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody AppointmentCreateRequest request) {
        AppointmentResponse response = bookingService.create(request);
        return ResponseEntity.created(URI.create("/api/appointments/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Search appointments",
            description = "Lists appointments filtered by branch, date, therapist, and status, sorted by start time.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged results, including an empty page when nothing matches"),
            @ApiResponse(responseCode = "400", description = "Invalid filter, page, or size",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AppointmentSearchResponse search(
            @Parameter(description = "Branch code such as RP") @RequestParam(required = false) String branchCode,
            @Parameter(description = "Clinic-local date (Asia/Bangkok), format 2026-09-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Therapist identifier") @RequestParam(required = false) Long therapistId,
            @Parameter(description = "SCHEDULED, CHECKED_IN, COMPLETED, or CANCELLED")
            @RequestParam(required = false) AppointmentStatus status,
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size from 1 to 100") @RequestParam(defaultValue = "20") int size) {
        return queryService.search(branchCode, date, therapistId, status, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get appointment", description = "Returns a single appointment with patient, therapist, and service summaries.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment found"),
            @ApiResponse(responseCode = "404", description = "Appointment not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AppointmentResponse getById(
            @Parameter(description = "Appointment database identifier", required = true) @PathVariable long id) {
        return queryService.getById(id);
    }

    @PostMapping("/{id}/check-in")
    @Operation(summary = "Check in appointment", description = "Marks a SCHEDULED appointment as CHECKED_IN when the patient arrives.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment checked in"),
            @ApiResponse(responseCode = "404", description = "Appointment not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Appointment is not in a state that allows check-in",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AppointmentResponse checkIn(
            @Parameter(description = "Appointment database identifier", required = true) @PathVariable long id) {
        return lifecycleService.checkIn(id);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete appointment", description = "Marks a CHECKED_IN appointment as COMPLETED.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment completed"),
            @ApiResponse(responseCode = "404", description = "Appointment not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Appointment must be checked in first",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AppointmentResponse complete(
            @Parameter(description = "Appointment database identifier", required = true) @PathVariable long id) {
        return lifecycleService.complete(id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel appointment", description = "Cancels a SCHEDULED or CHECKED_IN appointment and stores the reason. The time slot becomes available again.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Appointment cancelled"),
            @ApiResponse(responseCode = "400", description = "Cancellation reason is missing",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Appointment not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Completed or already cancelled appointments cannot be cancelled",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AppointmentResponse cancel(
            @Parameter(description = "Appointment database identifier", required = true) @PathVariable long id,
            @Valid @RequestBody AppointmentCancelRequest request) {
        return lifecycleService.cancel(id, request);
    }
}