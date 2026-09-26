package com.sabyeguyz.clinic.course;
import java.time.Instant;
public record CourseTransferResponse(Long id, Long sourcePurchaseId, Long destinationPurchaseId,
        Long sourcePatientId, String sourcePatientHn, String sourcePatientName,
        Long destinationPatientId, String destinationPatientHn, String destinationPatientName,
        Long courseId, String branchCode,
        int quantity, Long actorId, String reason, Instant createdAt) {
    static CourseTransferResponse from(CourseTransfer transfer) {
        return new CourseTransferResponse(transfer.getId(), transfer.getSource().getId(),
                transfer.getDestination().getId(), transfer.getSource().getPatient().getId(),
                transfer.getSource().getPatient().getHn(), transfer.getSource().getPatient().getFullName(),
                transfer.getDestination().getPatient().getId(), transfer.getDestination().getPatient().getHn(),
                transfer.getDestination().getPatient().getFullName(), transfer.getSource().getCourse().getId(),
                transfer.getSource().getBranch().getBranchCode(), transfer.getQuantity(), transfer.getActorId(),
                transfer.getReason(), transfer.getCreatedAt());
    }
}
