package com.sabyeguyz.clinic.course;

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
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!database-v2")
@RequestMapping("/api")
@Tag(name = "Course Ledger", description = "Course purchase, usage, and balance APIs")
public class CourseLedgerController {
    private final CourseLedgerService service;

    public CourseLedgerController(CourseLedgerService service) {
        this.service = service;
    }

    @PostMapping("/course-purchases")
    @Operation(summary = "Purchase a course",
            description = "Sells a course package to a patient. Price, sessions, bonus sessions, and validity are "
                    + "snapshotted from the course master, so later edits to the master do not change sold packages. "
                    + "The full entitlement is credited to the ledger immediately, so buying and using on the same day works.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Course purchased and entitlement credited"),
            @ApiResponse(responseCode = "400", description = "Validation failed, or the patient, course, or branch is inactive",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Patient or course not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CoursePurchaseResponse> purchase(@Valid @RequestBody CoursePurchaseRequest request) {
        CoursePurchaseResponse response = service.purchase(request);
        return ResponseEntity.created(URI.create("/api/course-purchases/" + response.id())).body(response);
    }

    @GetMapping("/patients/{patientId}/courses")
    @Operation(summary = "List a patient's courses",
            description = "Returns every course the patient has bought with purchased, used, and remaining counts, "
                    + "newest first. Use the `usable` flag to decide whether a course can be consumed right now.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Courses returned, empty list when the patient has none"),
            @ApiResponse(responseCode = "404", description = "Patient not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<CoursePurchaseResponse> listByPatient(
            @Parameter(description = "Patient database identifier", required = true) @PathVariable long patientId) {
        return service.listByPatient(patientId);
    }

    @GetMapping("/course-purchases/{id}")
    @Operation(summary = "Get a course ledger",
            description = "Returns the purchase together with every movement that changed its balance, oldest first, "
                    + "each with the running balance after it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ledger returned"),
            @ApiResponse(responseCode = "404", description = "Course purchase not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CourseLedgerResponse ledger(
            @Parameter(description = "Course purchase database identifier", required = true) @PathVariable long id) {
        return service.ledger(id);
    }

    @PostMapping("/course-usages")
    @Operation(summary = "Consume one session",
            description = "Deducts a single session from the course. Rejects expired, cancelled, and exhausted courses. "
                    + "Concurrent requests for the last remaining session are serialised so the balance never goes negative.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session consumed"),
            @ApiResponse(responseCode = "400", description = "Validation failed or the appointment belongs to another patient",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Course purchase or appointment not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Course is expired, cancelled, or has no sessions left",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CoursePurchaseResponse> use(@Valid @RequestBody CourseUsageRequest request) {
        return ResponseEntity.status(201).body(service.use(request));
    }

    @PostMapping("/course-movements/{movementId}/reverse")
    @Operation(summary = "Reverse a session usage",
            description = "Adds an opposite movement that gives the session back. The original movement is never "
                    + "deleted or edited. A movement can only be reversed once.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usage reversed and the session credited back"),
            @ApiResponse(responseCode = "400", description = "Reason is missing",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Movement does not exist, is not a usage, or was already reversed",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CoursePurchaseResponse reverseUsage(
            @Parameter(description = "Course movement database identifier", required = true) @PathVariable long movementId,
            @Valid @RequestBody CourseReasonRequest request) {
        return service.reverseUsage(movementId, request);
    }

    @PostMapping("/course-purchases/{id}/cancel")
    @Operation(summary = "Cancel a course purchase",
            description = "Writes off the remaining balance with a reversal movement and blocks further usage. "
                    + "Sessions already consumed stay in the ledger because they really happened. "
                    + "Refunding the money is not part of this endpoint.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Course cancelled and remaining balance written off"),
            @ApiResponse(responseCode = "400", description = "Reason is missing",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Course purchase not found",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Course was already cancelled",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CoursePurchaseResponse cancel(
            @Parameter(description = "Course purchase database identifier", required = true) @PathVariable long id,
            @Valid @RequestBody CourseReasonRequest request) {
        return service.cancel(id, request);
    }
}
