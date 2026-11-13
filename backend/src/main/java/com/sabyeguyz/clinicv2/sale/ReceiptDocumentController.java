package com.sabyeguyz.clinicv2.sale;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sabyeguyz.clinicv2.enums.ReasonCode;

/** New routes accept Receipt IDs; /api/receipts/{id} keeps its legacy SalesTransaction ID. */
@RestController
@PreAuthorize("hasAnyRole('MANAGER','THERAPIST')")
public class ReceiptDocumentController {
    private final ReceiptDocumentReadService documents;
    private final CoursePaymentVoidService voids;

    public ReceiptDocumentController(
            ReceiptDocumentReadService documents,
            CoursePaymentVoidService voids) {
        this.documents = documents;
        this.voids = voids;
    }

    public record VoidRequest(
            @NotNull ReasonCode reasonCode,
            @NotBlank @Size(max = 255) String reason) {}

    @GetMapping("/api/receipt-documents")
    public ReceiptDocumentReadService.PageResponse search(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) String branchCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documents.search(
                patientId,
                branchCode,
                page,
                size);
    }

    @GetMapping("/api/receipt-documents/{receiptId}")
    public ReceiptDocumentReadService.Document detail(@PathVariable Long receiptId) {
        return documents.getDetail(receiptId);
    }

    @GetMapping("/api/courses/{courseId}/receipt-history")
    public List<ReceiptDocumentReadService.Document> courseHistory(@PathVariable Long courseId) {
        return documents.getCourseHistory(courseId);
    }

    @PostMapping("/api/receipt-documents/{receiptId}/void")
    public CoursePaymentVoidService.Result voidReceipt(
            @PathVariable Long receiptId,
            @Valid @RequestBody VoidRequest request) {
        return voids.voidReceipt(
                receiptId,
                request.reasonCode(),
                request.reason());
    }
}
