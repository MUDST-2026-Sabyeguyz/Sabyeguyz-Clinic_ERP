package com.sabyeguyz.clinic.patient;

import com.sabyeguyz.clinic.api.ApiErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "Patients", description = "Patient registration, search, and profile APIs")
public class PatientController {
    private final PatientRegistrationService registrationService;
    private final PatientQueryService queryService;

    public PatientController(PatientRegistrationService registrationService, PatientQueryService queryService) {
        this.registrationService = registrationService;
        this.queryService = queryService;
    }

    @PostMapping
    @Operation(summary = "Register patient", description = "Registers a patient and generates an HN for the selected branch.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Patient registered"),
            @ApiResponse(responseCode = "400", description = "Request validation failed or patient data conflicts",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<PatientResponse> register(@Valid @RequestBody PatientRegistrationRequest request) {
        PatientResponse response = registrationService.register(request);
        return ResponseEntity.created(URI.create("/api/patients/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Search patients", description = "Temporarily searches HN, full name, phone, or identity number using case-insensitive partial matching.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paged search results, including an empty page when no patient matches"),
            @ApiResponse(responseCode = "400", description = "Invalid page or size",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PatientSearchResponse search(
            @Parameter(description = "Text matched against HN, full name, phone, and identity number")
            @RequestParam(defaultValue = "") String query,
            @RequestParam(required = false) String branchCode,
            @RequestParam(defaultValue = "nameAsc") String sort,
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size from 1 to 100") @RequestParam(defaultValue = "20") int size) {
        return queryService.search(query, branchCode, sort, page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get patient profile", description = "Returns the patient profile supported by the current schema, including a branch summary.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Patient profile"),
            @ApiResponse(responseCode = "404", description = "Patient not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PatientProfileResponse getProfile(
            @Parameter(description = "Patient database identifier", required = true) @PathVariable long id) {
        return queryService.getProfile(id);
    }

    @PutMapping("/{id}")
    public PatientProfileResponse update(@PathVariable long id, @Valid @RequestBody PatientUpdateRequest request) {
        return queryService.update(id, request);
    }
}
