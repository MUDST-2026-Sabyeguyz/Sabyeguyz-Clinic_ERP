package com.sabyeguyz.clinicv2.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.patient.Patient;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentTransactionServiceTest {
    @Mock PaymentTransactionRepository repository;

    private PaymentTransactionService service() {
        return new PaymentTransactionService(repository);
    }

    @Test
    void recordsAPaymentAgainstAnActiveTransaction() {
        SalesTransaction transaction = new SalesTransaction();
        transaction.setTransactionStatus(true);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentTransaction payment = service().record(new Patient(), new Branch(), transaction,
                new BigDecimal("1200.00"), new PaymentMethod(), 1L);

        assertThat(payment.getAmount()).isEqualByComparingTo("1200.00");
        assertThat(payment.isPaymentStatus()).isTrue();
    }

    @Test
    void rejectsPaymentAgainstAVoidedTransaction() {
        SalesTransaction transaction = new SalesTransaction();
        transaction.setTransactionStatus(false);

        assertThatThrownBy(() -> service().record(new Patient(), new Branch(), transaction,
                new BigDecimal("1200.00"), new PaymentMethod(), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroOrNegativeAmount() {
        SalesTransaction transaction = new SalesTransaction();
        transaction.setTransactionStatus(true);

        assertThatThrownBy(() -> service().record(new Patient(), new Branch(), transaction,
                BigDecimal.ZERO, new PaymentMethod(), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
