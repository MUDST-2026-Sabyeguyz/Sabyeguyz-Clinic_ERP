package com.sabyeguyz.clinicv2.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {
    @Mock BranchRepository repository;

    @Test
    void createsBranchWithValidTwoDigitCode() {
        when(repository.findByBranchCode("01")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Branch branch = new BranchService(repository).create("01", "สาขาราชพฤกษ์", "ที่อยู่", "0800000000", 1L);

        assertThat(branch.getBranchCode()).isEqualTo("01");
        assertThat(branch.isBranchStatus()).isTrue();
        assertThat(branch.getBranchCreateBy()).isEqualTo(1L);
    }

    @Test
    void rejectsNonNumericBranchCode() {
        assertThatThrownBy(() -> new BranchService(repository).create("RP", "ชื่อ", "ที่อยู่", "080", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsDuplicateBranchCode() {
        when(repository.findByBranchCode("01")).thenReturn(Optional.of(new Branch()));
        assertThatThrownBy(() -> new BranchService(repository).create("01", "ชื่อ", "ที่อยู่", "080", 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ซ้ำ");
    }

    @Test
    void rejectsBlankAddress() {
        when(repository.findByBranchCode("03")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new BranchService(repository).create("03", "ชื่อ", "  ", "080", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateNeverTouchesBranchCode() {
        Branch existing = new Branch();
        existing.setBranchCode("02");
        when(repository.findById(10L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Branch updated = new BranchService(repository).update(10L, "ชื่อใหม่", "ที่อยู่ใหม่", "081", 2L);

        assertThat(updated.getBranchCode()).isEqualTo("02");
        assertThat(updated.getBranchName()).isEqualTo("ชื่อใหม่");
        assertThat(updated.getBranchUpdateBy()).isEqualTo(2L);
    }

    @Test
    void deactivateThrowsWhenBranchMissing() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new BranchService(repository).deactivate(999L, 1L))
                .isInstanceOf(BranchNotFoundException.class);
    }
}
