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

@RestController
@RequestMapping("/api/appointments")
@Tag(name = "Appointments", description = "Appointment booking APIs")
public class AppointmentController {
    private final AppointmentBookingService bookingService;

    public AppointmentController(AppointmentBookingService bookingService) {
        this.bookingService = bookingService;
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
}