package com.sabyeguyz.clinicv2.sale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.catalog.CourseService;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.enums.ServiceType;
import com.sabyeguyz.clinicv2.enums.TransactionType;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesTransactionServiceTest {
    @Mock SalesTransactionRepository transactions;
    @Mock CourseService courseService;

    private SalesTransactionService service() {
        return new SalesTransactionService(transactions, courseService);
    }

    private static Service assessment() {
        Service service = new Service();
        service.setServiceType(ServiceType.Assessment);
        service.setServicePrice(new BigDecimal("1200.00"));
        return service;
    }

    @Test
    void recordsAnAssessmentSale() {
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SalesTransaction transaction = service().recordVisitSale(new Patient(), new Branch(), assessment(),
                TransactionType.Assessment, new Staff(), new Staff(), 1, null, null, 1L);

        assertThat(transaction.getAmount()).isEqualByComparingTo("1200.00");
        assertThat(transaction.isTransactionStatus()).isTrue();
    }

    @Test
    void rejectsCoursePurchaseInRecordVisitSale() {
        assertThatThrownBy(() -> service().recordVisitSale(new Patient(), new Branch(), assessment(),
                TransactionType.CoursePurchase, null, new Staff(), 1, null, null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void coursePurchaseCreatesTheCourseFirstThenTheTransaction() {
        Course course = new Course();
        course.setCoursePrice(new BigDecimal("10000.00"));
        when(courseService.sell(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(course);
        when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SalesTransaction transaction = service().recordCoursePurchase(new Patient(), new Branch(), new Receipt(),
                assessment(), new Staff(), null, null, null, null, null, 1L);

        assertThat(transaction.getCourse()).isSameAs(course);
        assertThat(transaction.getAmount()).isEqualByComparingTo("10000.00");
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.CoursePurchase);
    }

    @Test
    void voidRejectsAnAlreadyVoidedTransaction() {
        SalesTransaction transaction = new SalesTransaction();
        transaction.setTransactionStatus(false);
        when(transactions.findById(1L)).thenReturn(java.util.Optional.of(transaction));

        assertThatThrownBy(() -> service().voidTransaction(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
