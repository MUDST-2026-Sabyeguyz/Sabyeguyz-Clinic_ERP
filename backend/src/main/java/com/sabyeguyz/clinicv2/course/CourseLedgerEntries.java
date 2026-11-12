package com.sabyeguyz.clinicv2.course;

import java.time.Instant;

import com.sabyeguyz.clinicv2.enums.CourseLedgerEntryType;

/** Builds the ledger entries that more than one service writes. */
final class CourseLedgerEntries {
    private CourseLedgerEntries() {}

    /** The opposite movement of {@code original}, for the same patient; the balance moves back by the same quantity. */
    static CourseLedger reversalOf(
            CourseLedger original,
            long balanceBefore,
            String remark,
            Long actorId) {
        CourseLedger entry = new CourseLedger();
        entry.setCourse(original.getCourse());
        entry.setPatient(original.getPatient());
        entry.setEntryType(CourseLedgerEntryType.Reversal);
        entry.setQtyChange(-original.getQtyChange());
        entry.setBalanceAfter((int) (balanceBefore - original.getQtyChange()));
        entry.setReversesLedger(original);
        entry.setRemark(remark);
        entry.setLedgerCreateAt(Instant.now());
        entry.setLedgerCreateBy(actorId);
        entry.setEventAt(Instant.now());

        return entry;
    }
}
