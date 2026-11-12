package com.sabyeguyz.clinicv2.course;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.enums.CourseMemberRole;
import com.sabyeguyz.clinicv2.enums.TransferStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.role.Permission;
import com.sabyeguyz.clinicv2.sale.TransactionCancellationService;

/**
 * Course Transfer (Clinic ERP Workflow Brief Section 6). Moves visit entitlements
 * from one patient's balance to another's on the same Course. The Course's
 * own remaining_visits total is unaffected - a transfer never changes how
 * many visits exist, only who they belong to (see CourseLedgerRepository's
 * note on per-patient vs. course-wide balances). The ledger stays
 * authoritative; each member's Course_entitlement row is kept in step with it.
 *
 * <p>A transfer can be reversed while the receiving patient still holds the
 * transferred sessions: both ledger entries are reversed, never deleted.</p>
 */
@Service
public class CourseTransferService {
    private final CourseTransferRepository transfers;
    private final CourseLedgerRepository ledger;
    private final CourseSharedMemberRepository sharedMembers;
    private final CourseEntitlementRepository entitlements;
    private final TransactionCancellationService cancellations;

    public CourseTransferService(
            CourseTransferRepository transfers,
            CourseLedgerRepository ledger,
            CourseSharedMemberRepository sharedMembers,
            CourseEntitlementRepository entitlements,
            TransactionCancellationService cancellations) {
        this.transfers = transfers;
        this.ledger = ledger;
        this.sharedMembers = sharedMembers;
        this.entitlements = entitlements;
        this.cancellations = cancellations;
    }

    @Transactional
    public CourseTransfer transfer(
            Course course,
            Patient fromPatient,
            Patient toPatient,
            int qty,
            String remark,
            Long actorId) {
        require(course != null, "ต้องระบุคอร์ส");
        require(fromPatient != null, "ต้องระบุผู้โอน");
        require(toPatient != null, "ต้องระบุผู้รับโอน");
        require(!fromPatient.getId().equals(toPatient.getId()), "ผู้โอนและผู้รับโอนต้องไม่ใช่คนเดียวกัน");
        require(qty >= 1, "จำนวนครั้งที่โอนต้องอย่างน้อย 1");

        long fromBalance = ledger.sumQtyChangeByCourseAndPatient(course.getId(), fromPatient.getId());

        if (qty > fromBalance) {
            throw new IllegalArgumentException("จำนวนครั้งคงเหลือของผู้โอนไม่พอ (คงเหลือ " + fromBalance + " ครั้ง)");
        }

        // the entitlement row references the member row, and the transfer/ledger rows reference the entitlement
        ensureSharedMember(course, toPatient);
        ensureEntitlement(course, toPatient);

        CourseTransfer record = new CourseTransfer();
        record.setBranch(course.getBranch());
        record.setCourse(course);
        record.setFromPatient(fromPatient);
        record.setToPatient(toPatient);
        record.setQtyTransferred(qty);
        record.setTransferDate(Instant.now());
        record.setRemark(remark);
        record.setTransferStatus(TransferStatus.Completed);
        record.setTransferCreateAt(Instant.now());
        record.setTransferCreateBy(actorId);

        CourseTransfer saved = transfers.save(record);

        writeLedgerEntry(course, fromPatient, -qty, saved, actorId, CourseLedgerEntryType.TransferOut);
        writeLedgerEntry(course, toPatient, qty, saved, actorId, CourseLedgerEntryType.TransferIn);

        moveEntitlement(course, fromPatient, -qty, fromBalance);
        moveEntitlement(course, toPatient, qty, 0);

        return saved;
    }

    /** Undoes a Completed transfer: the sessions go back to the sender, provided the receiver has not used them. */
    @Transactional
    public CourseTransfer reverse(
            CourseTransfer transfer,
            Permission permission,
            String reason,
            Long actorId) {
        require(transfer != null, "ต้องระบุรายการโอน");
        require(transfer.getTransferStatus() == TransferStatus.Completed, "รายการโอนนี้ถูกย้อนไปแล้ว");

        Course course = transfer.getCourse();
        int qty = transfer.getQtyTransferred();
        Patient from = transfer.getFromPatient();
        Patient to = transfer.getToPatient();

        long toBalance = ledger.sumQtyChangeByCourseAndPatient(course.getId(), to.getId());

        if (toBalance < qty) {
            throw new IllegalArgumentException(
                    "ผู้รับโอนใช้สิทธิ์ไปแล้ว ไม่สามารถย้อนการโอนได้ (คงเหลือ " + toBalance + " ครั้ง)");
        }

        cancellations.recordTransferReversal(transfer, permission, reason, actorId);

        transfer.setTransferStatus(TransferStatus.Reversed);
        transfer.setTransferUpdateAt(Instant.now());
        transfer.setTransferUpdateBy(actorId);
        transfers.save(transfer);

        for (CourseLedger original : ledger.findByTransferId(transfer.getId())) {
            long balance = ledger.sumQtyChangeByCourseAndPatient(course.getId(), original.getPatient().getId());
            ledger.save(CourseLedgerEntries.reversalOf(original, balance, reason.trim(), actorId));
        }

        moveEntitlement(course, from, qty, 0);
        moveEntitlement(course, to, -qty, qty);

        return transfer;
    }

    private void writeLedgerEntry(
            Course course,
            Patient patient,
            int qtyChange,
            CourseTransfer transfer,
            Long actorId,
            CourseLedgerEntryType type) {
        long balanceAfter = ledger.sumQtyChangeByCourseAndPatient(course.getId(), patient.getId()) + qtyChange;

        CourseLedger entry = new CourseLedger();
        entry.setCourse(course);
        entry.setPatient(patient);
        entry.setEntryType(type);
        entry.setQtyChange(qtyChange);
        entry.setBalanceAfter((int) balanceAfter);
        entry.setTransfer(transfer);
        entry.setLedgerCreateAt(Instant.now());
        entry.setLedgerCreateBy(actorId);
        entry.setEventAt(Instant.now());
        ledger.save(entry);
    }

    /** The patient's entitlement row (0 visits when new); the transfer and ledger rows reference it. */
    private void ensureEntitlement(
            Course course,
            Patient patient) {
        CourseEntitlementId id = new CourseEntitlementId(course.getId(), patient.getId());

        if (entitlements.findById(id).isEmpty()) {
            entitlements.save(new CourseEntitlement(
                    course,
                    patient,
                    0));
        }
    }

    /** A transfer implicitly makes the receiving patient a Shared Member if they were not already on the Course. */
    private void ensureSharedMember(
            Course course,
            Patient patient) {
        CourseSharedMemberId id = new CourseSharedMemberId(course.getId(), patient.getId());

        if (sharedMembers.findById(id).isEmpty()) {
            sharedMembers.save(new CourseSharedMember(
                    course,
                    patient,
                    CourseMemberRole.SharedMember));
        }
    }

    /** Applies the session change to the member's entitlement, creating it (with {@code startingAllocation}) if missing. */
    private void moveEntitlement(
            Course course,
            Patient patient,
            int delta,
            long startingAllocation) {
        CourseEntitlement entitlement = entitlements.findById(new CourseEntitlementId(course.getId(), patient.getId()))
                .orElseGet(() -> new CourseEntitlement(
                        course,
                        patient,
                        (int) startingAllocation));
        entitlement.adjustAllocated(delta);
        entitlements.save(entitlement);
    }

    private static void require(
            boolean condition,
            String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
