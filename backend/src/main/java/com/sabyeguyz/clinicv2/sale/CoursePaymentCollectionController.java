package com.sabyeguyz.clinicv2.sale;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasAnyRole('MANAGER', 'THERAPIST')")
public class CoursePaymentCollectionController {
    private final CoursePaymentCollectionRequestService requests;

    public CoursePaymentCollectionController(CoursePaymentCollectionRequestService requests) {
        this.requests = requests;
    }

    /** Replays the original 201 body for the same actor, key and normalized payload. */
    @PostMapping("/api/course-payment-collections")
    @ResponseStatus(HttpStatus.CREATED)
    public CoursePaymentCollectionService.Result collect(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CoursePaymentCollectionRequest request) {
        return requests.collect(key, request);
    }
}
