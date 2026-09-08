package com.sabyeguyz.clinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AppointmentStateTest {

    @Test
    void newAppointmentStartsAsConfirmed() {
        assertThat(confirmed().getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void checkInMovesConfirmedToArrived() {
        Appointment appointment = confirmed();
        appointment.markArrived();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.ARRIVED);
    }

    @Test
    void startServiceMovesArrivedToInService() {
        Appointment appointment = confirmed();
        appointment.markArrived();
        appointment.startService();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.IN_SERVICE);
    }

    @Test
    void completeMovesInServiceToCompleted() {
        Appointment appointment = inService();

        appointment.complete();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void rescheduleMovesConfirmedToRescheduled() {
        Appointment appointment = confirmed();
        Instant newStart = Instant.now().plus(Duration.ofDays(3));

        appointment.reschedule(null, newStart, newStart.plus(Duration.ofHours(1)));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
        assertThat(appointment.getStartAt()).isEqualTo(newStart);
    }

    @Test
    void rescheduledAppointmentCanBeRescheduledAgain() {
        Appointment appointment = rescheduled();
        Instant newStart = Instant.now().plus(Duration.ofDays(5));

        appointment.reschedule(null, newStart, newStart.plus(Duration.ofHours(1)));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.RESCHEDULED);
    }

    @Test
    void rescheduledAppointmentCanCheckIn() {
        Appointment appointment = rescheduled();

        appointment.markArrived();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.ARRIVED);
    }

    @Test
    void noShowMovesConfirmedToNoShow() {
        Appointment appointment = confirmed();
        appointment.markNoShow();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    void cancelStoresReason() {
        Appointment appointment = confirmed();
        appointment.cancel("ผู้ป่วยติดธุระ");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancelReason()).isEqualTo("ผู้ป่วยติดธุระ");
    }

    @Test
    void cancelIsAllowedAfterArrival() {
        Appointment appointment = confirmed();
        appointment.markArrived();
        appointment.cancel("ผู้ป่วยขอกลับก่อน");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void rejectsStartServiceBeforeArrival() {
        assertThatThrownBy(() -> confirmed().startService())
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("CONFIRMED");
    }

    @Test
    void rejectsCompleteBeforeServiceStarted() {
        Appointment appointment = confirmed();
        appointment.markArrived();

        assertThatThrownBy(appointment::complete)
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("ARRIVED");
    }

    @Test
    void rejectsSecondCheckIn() {
        Appointment appointment = confirmed();
        appointment.markArrived();

        assertThatThrownBy(appointment::markArrived)
                .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    void rejectsCancelAfterCompleted() {
        Appointment appointment = inService();
        appointment.complete();

        assertThatThrownBy(() -> appointment.cancel("เปลี่ยนใจ"))
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void rejectsCancelAfterServiceStarted() {
        assertThatThrownBy(() -> inService().cancel("เปลี่ยนใจ"))
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("IN_SERVICE");
    }

    @Test
    void rejectsCheckInAfterCancel() {
        Appointment appointment = confirmed();
        appointment.cancel("ผู้ป่วยยกเลิก");

        assertThatThrownBy(appointment::markArrived)
                .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    void rejectsNoShowAfterArrival() {
        Appointment appointment = confirmed();
        appointment.markArrived();

        assertThatThrownBy(appointment::markNoShow)
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("ARRIVED");
    }

    @Test
    void rejectsRescheduleAfterArrival() {
        Appointment appointment = confirmed();
        appointment.markArrived();
        Instant newStart = Instant.now().plus(Duration.ofDays(2));

        assertThatThrownBy(() -> appointment.reschedule(null, newStart, newStart.plus(Duration.ofHours(1))))
                .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    void cancelledAndNoShowReleaseTheTimeSlot() {
        assertThat(AppointmentStatus.slotReleasing())
                .containsExactlyInAnyOrder(AppointmentStatus.CANCELLED, AppointmentStatus.NO_SHOW);
    }

    @Test
    void updatesTimestampOnTransition() throws Exception {
        Appointment appointment = confirmed();
        Instant before = appointment.getUpdatedAt();
        Thread.sleep(5);

        appointment.markArrived();

        assertThat(appointment.getUpdatedAt()).isAfter(before);
    }

    private Appointment confirmed() {
        Instant startAt = Instant.now().plus(Duration.ofDays(1));
        return new Appointment(null, null, null, null, startAt, startAt.plus(Duration.ofHours(1)), null);
    }

    private Appointment rescheduled() {
        Appointment appointment = confirmed();
        Instant newStart = Instant.now().plus(Duration.ofDays(2));
        appointment.reschedule(null, newStart, newStart.plus(Duration.ofHours(1)));
        return appointment;
    }

    private Appointment inService() {
        Appointment appointment = confirmed();
        appointment.markArrived();
        appointment.startService();
        return appointment;
    }
}
