package com.sabyeguyz.clinicv2.staff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.CommissionAfterTerminationPolicy;
import com.sabyeguyz.clinicv2.enums.StaffPosition;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {
    @Mock StaffRepository repository;

    @Test
    void createsStaffAssignedToABranch() {
        when(repository.findByStaffCode("THP-001")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Branch branch = new Branch();

        Staff staff = new StaffService(repository).create(
                "THP-001", "สมชาย", "ใจดี", StaffPosition.Therapist, branch, LocalDate.of(2026, 1, 1), 1L);

        assertThat(staff.getStaffCode()).isEqualTo("THP-001");
        assertThat(staff.isStaffStatus()).isTrue();
        assertThat(staff.getBranch()).isSameAs(branch);
    }

    @Test
    void rejectsDuplicateStaffCode() {
        when(repository.findByStaffCode("THP-001")).thenReturn(Optional.of(new Staff()));
        assertThatThrownBy(() -> new StaffService(repository).create(
                "THP-001", "สมชาย", "ใจดี", StaffPosition.Therapist, new Branch(), LocalDate.now(), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ซ้ำ");
    }

    @Test
    void rejectsMissingBranch() {
        when(repository.findByStaffCode("THP-002")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> new StaffService(repository).create(
                "THP-002", "สมหญิง", "ใจดี", StaffPosition.Therapist, null, LocalDate.now(), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminateSetsStatusAndPolicy() {
        Staff staff = new Staff();
        staff.setStaffStatus(true);
        when(repository.findById(5L)).thenReturn(Optional.of(staff));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Staff terminated = new StaffService(repository).terminate(
                5L, LocalDate.of(2026, 6, 30), CommissionAfterTerminationPolicy.CONTINUE_UNTIL_COMPLETE, 9L);

        assertThat(terminated.isStaffStatus()).isFalse();
        assertThat(terminated.getTerminationDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(terminated.getCommissionAfterTerminationPolicy())
                .isEqualTo(CommissionAfterTerminationPolicy.CONTINUE_UNTIL_COMPLETE);
    }

    @Test
    void terminateRequiresAPolicy() {
        when(repository.findById(5L)).thenReturn(Optional.of(new Staff()));
        assertThatThrownBy(() -> new StaffService(repository).terminate(5L, LocalDate.now(), null, 9L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
