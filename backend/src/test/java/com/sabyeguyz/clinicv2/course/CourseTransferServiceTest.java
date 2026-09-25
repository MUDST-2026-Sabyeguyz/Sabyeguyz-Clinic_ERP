package com.sabyeguyz.clinicv2.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.catalog.Course;
import com.sabyeguyz.clinicv2.patient.Patient;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseTransferServiceTest {
    @Mock CourseTransferRepository transfers;
    @Mock CourseLedgerRepository ledger;
    @Mock CourseSharedMemberRepository sharedMembers;

    private CourseTransferService service() {
        return new CourseTransferService(transfers, ledger, sharedMembers);
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void transfersVisitsAndWritesBothLedgerEntries() {
        Course course = new Course();
        setId(course, 1L);
        Patient from = new Patient();
        setId(from, 10L);
        Patient to = new Patient();
        setId(to, 20L);

        when(ledger.sumQtyChangeByCourseAndPatient(1L, 10L)).thenReturn(6L);
        when(ledger.sumQtyChangeByCourseAndPatient(1L, 20L)).thenReturn(0L);
        when(transfers.save(any())).thenAnswer(invocation -> {
            CourseTransfer saved = invocation.getArgument(0);
            setId(saved, 100L);
            return saved;
        });
        when(sharedMembers.findById(new CourseSharedMemberId(1L, 20L))).thenReturn(Optional.empty());

        CourseTransfer transfer = service().transfer(course, from, to, 4, "ย้ายให้ครอบครัว", 1L);

        assertThat(transfer.getQtyTransferred()).isEqualTo(4);
        org.mockito.Mockito.verify(ledger, org.mockito.Mockito.times(2)).save(any());
        org.mockito.Mockito.verify(sharedMembers).save(any());
    }

    @Test
    void rejectsTransferExceedingTheBalance() {
        Course course = new Course();
        setId(course, 1L);
        Patient from = new Patient();
        setId(from, 10L);
        Patient to = new Patient();
        setId(to, 20L);
        when(ledger.sumQtyChangeByCourseAndPatient(1L, 10L)).thenReturn(2L);

        assertThatThrownBy(() -> service().transfer(course, from, to, 4, null, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ไม่พอ");
    }

    @Test
    void rejectsTransferringToOneself() {
        Course course = new Course();
        setId(course, 1L);
        Patient patient = new Patient();
        setId(patient, 10L);

        assertThatThrownBy(() -> service().transfer(course, patient, patient, 1, null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
