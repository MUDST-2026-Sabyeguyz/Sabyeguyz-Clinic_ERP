package com.sabyeguyz.clinic.course;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@Profile("!database-v2")
@RequestMapping("/api/course-transfers")
public class CourseTransferController {
    private final CourseTransferService service;
    public CourseTransferController(CourseTransferService service) { this.service = service; }
    @PostMapping public ResponseEntity<CourseTransferResponse> transfer(@Valid @RequestBody CourseTransferRequest request) {
        return ResponseEntity.status(201).body(service.transfer(request));
    }
    @GetMapping public List<CourseTransferResponse> history(@RequestParam(required = false) Long patientId) {
        return service.history(patientId);
    }
}
