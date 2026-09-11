package com.sabyeguyz.clinic.treatment;
import com.sabyeguyz.clinic.api.ApiErrorResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/treatments")
public class TreatmentController {
    private final TreatmentService service;
    public TreatmentController(TreatmentService service) { this.service=service; }
    @PostMapping
    public ResponseEntity<TreatmentResponse> create(@Valid @RequestBody TreatmentRequest request) {
        var result=service.create(request);
        return ResponseEntity.created(URI.create("/api/treatments/"+result.id())).body(result);
    }
    @GetMapping("/{id}")
    public TreatmentResponse detail(@PathVariable long id) { return service.detail(id); }
    @GetMapping
    public Page<TreatmentResponse> history(@RequestParam(required=false) Long patientId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.history(patientId,page,size);
    }
    @ExceptionHandler(TreatmentNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(TreatmentNotFoundException exception) {
        return ResponseEntity.status(404).body(new ApiErrorResponse(exception.getMessage()));
    }
}
