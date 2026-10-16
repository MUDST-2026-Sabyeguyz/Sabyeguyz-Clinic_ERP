package com.sabyeguyz.clinicv2.patient;

import com.sabyeguyz.clinicv2.branch.Branch;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HN format per the Patient data dictionary: YYBBMMNNNN
 * YY = 2-digit CE year, BB = 2-digit branch_code, MM = 2-digit month,
 * NNNN = 4-digit running number, reset every month per branch.
 *
 * <p>Concurrency note: this reads-then-writes the {@link HnSequence} row
 * inside the caller's transaction. Once the schema is live, the running
 * number column should be updated with a row lock (e.g. a pessimistic
 * read or a native "FOR UPDATE"/upsert) the same way the old HnGenerator
 * did — plain JPA optimistic locking is not enough to serialize two
 * concurrent registrations for the same branch and month.</p>
 */
@Service
public class HnGeneratorService {
    private final HnSequenceRepository sequences;

    public HnGeneratorService(HnSequenceRepository sequences) {
        this.sequences = sequences;
    }

    @Transactional
    public String next(Branch branch, LocalDate registrationDate) {
        if (branch == null || branch.getBranchCode() == null) {
            throw new IllegalArgumentException("ต้องระบุสาขาที่มี branchCode ก่อนออก HN");
        }
        String yearMonth = "%02d%02d".formatted(registrationDate.getYear() % 100, registrationDate.getMonthValue());
        HnSequenceId id = new HnSequenceId(branch.getId(), yearMonth);
        HnSequence sequence = sequences.findById(id).orElseGet(() -> {
            HnSequence created = new HnSequence(branch, yearMonth, 0, Instant.now(), Instant.now());
            return sequences.save(created);
        });

        int nextRunningNo = sequence.getLastRunningNo() + 1;
        if (nextRunningNo > 9999) {
            throw new IllegalStateException("HN running number เต็มสำหรับสาขาและเดือนนี้");
        }
        sequence.setLastRunningNo(nextRunningNo);
        sequence.setLastRunningAt(Instant.now());
        sequences.save(sequence);

        return format(registrationDate, branch.getBranchCode(), nextRunningNo);
    }

    static String format(LocalDate registrationDate, String branchCode, int runningNumber) {
        return "%02d%s%02d%04d".formatted(
                registrationDate.getYear() % 100, branchCode, registrationDate.getMonthValue(), runningNumber);
    }
}
