package com.sabyeguyz.clinicv2.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.catalog.Service;
import com.sabyeguyz.clinicv2.enums.AppointmentStatus;
import com.sabyeguyz.clinicv2.patient.Patient;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {
    @Mock AppointmentRepository appointments;
    @Mock AppointmentStatusHistoryRepository history;

    private AppointmentService service() {
        return new AppointmentService(appointments, history);
    }

    @Test
    void booksAConfirmedAppointment() {
        when(appointments.existsByStaffIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNotIn(
                any(), any(), any(), any())).thenReturn(false);
        when(appointments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment appointment = service().book(new Patient(), new Branch(), new Staff(), new Service(), null,
                LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), 1L);

        assertThat(appointment.getAppointmentStatus()).isEqualTo(AppointmentStatus.Confirmed);
    }

    @Test
    void rejectsBookingWhenStaffAlreadyBooked() {
        when(appointments.existsByStaffIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNotIn(
                any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service().book(new Patient(), new Branch(), new Staff(), new Service(), null,
                LocalDate.of(2026, 12, 1), LocalTime.of(9, 0), 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cancelRecordsStatusHistory() {
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.Confirmed);
        when(appointments.findById(1L)).thenReturn(java.util.Optional.of(appointment));
        when(appointments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment cancelled = service().cancel(1L, "ผู้ป่วยขอยกเลิก", 9L);

        assertThat(cancelled.getAppointmentStatus()).isEqualTo(AppointmentStatus.Cancelled);
        org.mockito.Mockito.verify(history).save(any());
    }

    @Test
    void rejectsCancelOnAlreadyCompletedAppointment() {
        Appointment appointment = new Appointment();
        appointment.setAppointmentStatus(AppointmentStatus.Completed);
        when(appointments.findById(1L)).thenReturn(java.util.Optional.of(appointment));

        assertThatThrownBy(() -> service().cancel(1L, "สาย", 9L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rescheduleMovesDateAndTimeAndKeepsConfirmedStatus() {
        Appointment appointment = new Appointment();
        appointment.setStaff(new Staff());
        appointment.setAppointmentStatus(AppointmentStatus.Confirmed);
        when(appointments.findById(1L)).thenReturn(java.util.Optional.of(appointment));
        when(appointments.existsByStaffIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNotIn(
                any(), any(), any(), any())).thenReturn(false);
        when(appointments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment rescheduled = service().reschedule(1L, LocalDate.of(2026, 12, 5), LocalTime.of(10, 0), "ผู้ป่วยขอเลื่อน", 9L);

        assertThat(rescheduled.getAppointmentDate()).isEqualTo(LocalDate.of(2026, 12, 5));
        assertThat(rescheduled.getAppointmentTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(rescheduled.getAppointmentStatus()).isEqualTo(AppointmentStatus.Confirmed);
    }
}
