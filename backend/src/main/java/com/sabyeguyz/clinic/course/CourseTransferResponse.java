package com.sabyeguyz.clinic.course;
import java.time.Instant;
public record CourseTransferResponse(Long id, Long sourcePurchaseId, Long destinationPurchaseId,
        Long sourcePatientId, Long destinationPatientId, Long courseId, String branchCode,
        int quantity, Long actorId, String reason, Instant createdAt) {
    static CourseTransferResponse from(CourseTransfer transfer) {
        return new CourseTransferResponse(transfer.getId(), transfer.getSource().getId(),
                transfer.getDestination().getId(), transfer.getSource().getPatient().getId(),
                transfer.getDestination().getPatient().getId(), transfer.getSource().getCourse().getId(),
                transfer.getSource().getBranch().getCode(), transfer.getQuantity(), transfer.getActorId(),
                transfer.getReason(), transfer.getCreatedAt());
    }
}
