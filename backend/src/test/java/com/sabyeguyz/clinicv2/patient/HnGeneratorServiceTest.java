package com.sabyeguyz.clinicv2.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.branch.Branch;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HnGeneratorServiceTest {
    @Mock HnSequenceRepository sequences;

    @Test
    void formatsYearBranchMonthAndRunningNumber() {
        assertThat(HnGeneratorService.format(LocalDate.of(2026, 12, 1), "01", 1)).isEqualTo("2601120001");
    }

    @Test
    void startsANewSequenceAtOne() {
        Branch branch = new Branch();
        branch.setBranchCode("01");
        when(sequences.findById(any())).thenReturn(Optional.empty());
        when(sequences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String hn = new HnGeneratorService(sequences).next(branch, LocalDate.of(2026, 12, 1));

        assertThat(hn).isEqualTo("2601120001");
    }

    @Test
    void incrementsAnExistingSequence() {
        Branch branch = new Branch();
        branch.setBranchCode("01");
        HnSequence existing = new HnSequence(branch, "2612", 4, java.time.Instant.now(), java.time.Instant.now());
        when(sequences.findById(any())).thenReturn(Optional.of(existing));
        when(sequences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String hn = new HnGeneratorService(sequences).next(branch, LocalDate.of(2026, 12, 1));

        assertThat(hn).isEqualTo("2601120005");
    }

    @Test
    void rejectsABranchWithoutACode() {
        assertThatThrownBy(() -> new HnGeneratorService(sequences).next(new Branch(), LocalDate.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
