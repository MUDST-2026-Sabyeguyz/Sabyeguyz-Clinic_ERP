package com.sabyeguyz.clinic.course;

import com.sabyeguyz.clinic.audit.AuditService;
import com.sabyeguyz.clinic.audit.TransactionActor;
import com.sabyeguyz.clinic.patient.PatientRepository;
import com.sabyeguyz.clinic.patient.PatientNotFoundException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseTransferService {
    private final CoursePurchaseRepository purchases;
    private final CourseMovementRepository movements;
    private final CourseTransferRepository transfers;
    private final PatientRepository patients;
    private final TransactionActor actor;
    private final AuditService audit;
    public CourseTransferService(CoursePurchaseRepository purchases, CourseMovementRepository movements,
            CourseTransferRepository transfers, PatientRepository patients, TransactionActor actor, AuditService audit) {
        this.purchases = purchases; this.movements = movements; this.transfers = transfers;
        this.patients = patients; this.actor = actor; this.audit = audit;
    }

    @Transactional
    public CourseTransferResponse transfer(CourseTransferRequest request) {
        Long actorId = actor.requireId();
        if (request.quantity() <= 0 || request.reason() == null || request.reason().isBlank()
                || request.reason().length() > 1000) throw new IllegalArgumentException("Positive quantity and reason required");
        CoursePurchase source = purchases.findByIdForUpdate(request.sourcePurchaseId())
                .orElseThrow(() -> new CoursePurchaseNotFoundException(request.sourcePurchaseId()));
        if (source.getStatus() != CoursePurchaseStatus.ACTIVE || source.isExpiredAt(Instant.now()))
            throw new CourseBalanceException("Source course is cancelled or expired");
        if (source.getPatient().getId().equals(request.destinationPatientId()))
            throw new IllegalArgumentException("Destination must be a different patient");
        var destinationPatient = patients.findById(request.destinationPatientId())
                .orElseThrow(() -> new PatientNotFoundException(request.destinationPatientId()));
        if (!destinationPatient.isActive()) throw new IllegalArgumentException("Destination patient is inactive");
        if (movements.balanceOf(source.getId()) < request.quantity())
            throw new CourseBalanceException("Insufficient course balance");
        // A distinct lot preserves the original expiry; never merge with a differently dated purchase.
        CoursePurchase destination = purchases.save(CoursePurchase.transferredFrom(source, destinationPatient, request.quantity()));
        CourseTransfer transfer = transfers.save(new CourseTransfer(source, destination, request.quantity(), actorId, request.reason()));
        movements.save(CourseMovement.transfer(source, CourseMovementType.TRANSFER_OUT, -request.quantity(), request.reason()));
        movements.save(CourseMovement.transfer(destination, CourseMovementType.TRANSFER_IN, request.quantity(), request.reason()));
        audit.record("TRANSFER", "COURSE_TRANSFER", transfer.getId());
        return CourseTransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public List<CourseTransferResponse> history(Long patientId) {
        return transfers.history(patientId).stream().map(CourseTransferResponse::from).toList();
    }
}
