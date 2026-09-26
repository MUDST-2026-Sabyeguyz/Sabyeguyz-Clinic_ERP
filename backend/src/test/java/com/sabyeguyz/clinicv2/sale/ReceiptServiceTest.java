package com.sabyeguyz.clinicv2.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.patient.Patient;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {
    @Mock ReceiptRepository repository;

    @Test
    void createsAReceipt() {
        when(repository.findByReceiptNo("RE-2600001")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Receipt receipt = new ReceiptService(repository).create(
                "RE-2600001", new Patient(), new Branch(), LocalDate.of(2026, 8, 18), 1L);

        assertThat(receipt.getReceiptNo()).isEqualTo("RE-2600001");
        assertThat(receipt.getSaleDate()).isEqualTo(LocalDate.of(2026, 8, 18));
    }

    @Test
    void rejectsDuplicateReceiptNo() {
        when(repository.findByReceiptNo("RE-2600001")).thenReturn(Optional.of(new Receipt()));
        assertThatThrownBy(() -> new ReceiptService(repository).create(
                "RE-2600001", new Patient(), new Branch(), LocalDate.now(), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
