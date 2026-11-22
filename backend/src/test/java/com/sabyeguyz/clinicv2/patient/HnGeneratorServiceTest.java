package com.sabyeguyz.clinicv2.patient;

import static org.junit.jupiter.api.Assertions.*;
import com.sabyeguyz.clinicv2.branch.Branch;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HnGeneratorServiceTest {
    private final Map<HnSequenceId, HnSequence> rows = new HashMap<>();
    private final HnSequenceRepository repository = (HnSequenceRepository) Proxy.newProxyInstance(
        HnSequenceRepository.class.getClassLoader(), new Class<?>[]{HnSequenceRepository.class},
        (proxy, method, args) -> switch (method.getName()) {
            case "findById" -> Optional.ofNullable(rows.get((HnSequenceId) args[0]));
            case "save" -> {
                HnSequence row = (HnSequence) args[0];
                rows.put(new HnSequenceId(row.getBranch().getId(), row.getYearMonth()), row);
                yield row;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    private final HnGeneratorService generator = new HnGeneratorService(repository);

    private Branch branch(long id, String code) throws Exception {
        Branch branch = new Branch();
        var field = Branch.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(branch, id);
        branch.setBranchCode(code);
        return branch;
    }

    @Test
    void incrementsWithinBranchAndResetsForNewMonthYearAndBranch() throws Exception {
        Branch one = branch(1L, "01");
        LocalDate october = LocalDate.of(2026, 10, 16);
        assertEquals("2601100001", generator.next(one, october));
        assertEquals("2601100002", generator.next(one, october));
        assertEquals("2602100001", generator.next(branch(2L, "02"), october));
        assertEquals("2601110001", generator.next(one, LocalDate.of(2026, 11, 1)));
        assertEquals("2701010001", generator.next(one, LocalDate.of(2027, 1, 1)));
    }

    @Test
    void refusesOverflowWithoutWrappingOrChangingSequence() throws Exception {
        Branch one = branch(1L, "01");
        HnSequence sequence = new HnSequence(one, "2610", 9998, Instant.EPOCH, Instant.EPOCH);
        rows.put(new HnSequenceId(1L, "2610"), sequence);
        assertEquals("2601109999", generator.next(one, LocalDate.of(2026, 10, 16)));
        assertThrows(IllegalStateException.class, () -> generator.next(one, LocalDate.of(2026, 10, 17)));
        assertEquals(9999, sequence.getLastRunningNo());
    }

    @Test
    void rejectsMissingBranchBeforeRepositoryUse() {
        assertThrows(IllegalArgumentException.class, () -> generator.next(null, LocalDate.of(2026, 10, 16)));
        assertThrows(IllegalArgumentException.class, () -> generator.next(new Branch(), LocalDate.of(2026, 10, 16)));
        assertTrue(rows.isEmpty());
    }
}
