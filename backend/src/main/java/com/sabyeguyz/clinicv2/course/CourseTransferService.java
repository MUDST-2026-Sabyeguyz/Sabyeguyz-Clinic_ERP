package com.sabyeguyz.clinicv2.course;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;
import com.sabyeguyz.clinicv2.enums.CourseMemberRole;
import com.sabyeguyz.clinicv2.patient.Patient;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Course Transfer (Clinic ERP Workflow Brief §6). Moves visit entitlements
 * from one patient's balance to another's on the same Course. The Course's
 * own remaining_visits total is unaffected — a transfer never changes how
 * many visits exist, only who they belong to (see CourseLedgerRepository's
 * note on per-patient vs. course-wide balances).
 */
@Service
public class CourseTransferService {
    private final CourseTransferRepository transfers;
    private final CourseLedgerRepository ledger;
    private final CourseSharedMemberRepository sharedMembers;

    public CourseTransferService(CourseTransferRepository transfers, CourseLedgerRepository ledger,
                                  CourseSharedMemberRepository sharedMembers) {
        this.transfers = transfers;
        this.ledger = ledger;
        this.sharedMembers = sharedMembers;
    }

    @Transactional
    public CourseTransfer transfer(Course course, Patient fromPatient, Patient toPatient, int qty,
                                    String remark, Long actorId) {
        require(course != null, "ต้องระบุคอร์ส");
        require(fromPatient != null, "ต้องระบุผู้โอน");
        require(toPatient != null, "ต้องระบุผู้รับโอน");
        require(!fromPatient.getId().equals(toPatient.getId()), "ผู้โอนและผู้รับโอนต้องไม่ใช่คนเดียวกัน");
        require(qty >= 1, "จำนวนครั้งที่โอนต้องอย่างน้อย 1");

        long fromBalance = ledger.sumQtyChangeByCourseAndPatient(course.getId(), fromPatient.getId());
        if (qty > fromBalance) {
            throw new IllegalArgumentException("จำนวนครั้งคงเหลือของผู้โอนไม่พอ (คงเหลือ " + fromBalance + " ครั้ง)");
        }

        CourseTransfer record = new CourseTransfer();
        record.setCourse(course);
        record.setFromPatient(fromPatient);
        record.setToPatient(toPatient);
        record.setQtyTransferred(qty);
        record.setTransferDate(Instant.now());
        record.setRemark(remark);
        record.setTransferCreateAt(Instant.now());
        record.setTransferCreateBy(actorId);
        CourseTransfer saved = transfers.save(record);

        writeLedgerEntry(course, fromPatient, -qty, saved, actorId, CourseLedgerEntryType.TransferOut);
        writeLedgerEntry(course, toPatient, qty, saved, actorId, CourseLedgerEntryType.TransferIn);

        ensureSharedMember(course, toPatient);

        return saved;
    }

    private void writeLedgerEntry(Course course, Patient patient, int qtyChange, CourseTransfer transfer,
                                   Long actorId, CourseLedgerEntryType type) {
        long balanceAfter = ledger.sumQtyChangeByCourseAndPatient(course.getId(), patient.getId()) + qtyChange;
        CourseLedger entry = new CourseLedger();
        entry.setCourse(course);
        entry.setPatient(patient);
        entry.setEntryType(type);
        entry.setQtyChange(qtyChange);
        entry.setBalanceAfter((int) balanceAfter);
        entry.setReferenceType("TRANSFER");
        entry.setReferenceId(transfer.getId());
        entry.setLedgerCreateAt(Instant.now());
        entry.setLedgerCreateBy(actorId);
        ledger.save(entry);
    }

    /** A transfer implicitly makes the receiving patient a Shared Member if they were not already on the Course. */
    private void ensureSharedMember(Course course, Patient patient) {
        CourseSharedMemberId id = new CourseSharedMemberId(course.getId(), patient.getId());
        if (sharedMembers.findById(id).isEmpty()) {
            sharedMembers.save(new CourseSharedMember(course, patient, CourseMemberRole.SharedMember));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
