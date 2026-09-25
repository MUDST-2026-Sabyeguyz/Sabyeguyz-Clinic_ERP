package com.sabyeguyz.clinicv2.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.enums.VisitStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatientVisitServiceTest {
    @Mock PatientVisitRepository visits;
    @Mock AppointmentService appointmentService;

    private PatientVisitService service() {
        return new PatientVisitService(visits, appointmentService);
    }

    @Test
    void checkInFromAppointmentMarksTheAppointmentArrived() {
        Appointment appointment = new Appointment();
        appointment.setPatient(new Patient());
        appointment.setBranch(new Branch());
        appointment.setStaff(new Staff());
        appointment.setAppointmentStatus(AppointmentStatus.Arrived);
        when(appointmentService.markArrived(5L, 1L)).thenReturn(appointment);
        when(visits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var visit = service().checkInFromAppointment(5L, 1L);

        assertThat(visit.getVisitStatus()).isEqualTo(VisitStatus.Arrived);
        assertThat(visit.getAppointment()).isSameAs(appointment);
        verify(appointmentService).markArrived(5L, 1L);
    }

    @Test
    void checkInWalkInRequiresAPatientAndBranch() {
        assertThatThrownBy(() -> service().checkInWalkIn(null, new Branch(), null, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completeSetsCheckoutTimeAndPropagatesToTheAppointment() {
        Appointment appointment = new Appointment();
        var visit = new com.sabyeguyz.clinicv2.appointment.PatientVisit();
        visit.setAppointment(appointment);
        visit.setVisitStatus(VisitStatus.InService);
        when(visits.findById(9L)).thenReturn(Optional.of(visit));
        when(visits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(appointmentService.markCompleted(any(), org.mockito.ArgumentMatchers.eq(1L))).thenReturn(appointment);

        var completed = service().complete(9L, 1L);

        assertThat(completed.getVisitStatus()).isEqualTo(VisitStatus.Completed);
        assertThat(completed.getCheckoutTime()).isNotNull();
    }

    @Test
    void rejectsProgressingACancelledVisit() {
        var visit = new com.sabyeguyz.clinicv2.appointment.PatientVisit();
        visit.setVisitStatus(VisitStatus.Cancelled);
        when(visits.findById(9L)).thenReturn(Optional.of(visit));

        assertThatThrownBy(() -> service().startService(9L, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
