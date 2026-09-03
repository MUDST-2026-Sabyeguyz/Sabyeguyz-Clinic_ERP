package com.sabyeguyz.clinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AppointmentStateTest {

    @Test
    void newAppointmentStartsAsScheduled() {
        assertThat(scheduled().getStatus()).isEqualTo(AppointmentStatus.SCHEDULED);
    }

    @Test
    void checkInMovesScheduledToCheckedIn() {
        Appointment appointment = scheduled();
        appointment.checkIn();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CHECKED_IN);
    }

    @Test
    void completeMovesCheckedInToCompleted() {
        Appointment appointment = scheduled();
        appointment.checkIn();
        appointment.complete();

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    @Test
    void cancelStoresReason() {
        Appointment appointment = scheduled();
        appointment.cancel("ผู้ป่วยติดธุระ");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointment.getCancelReason()).isEqualTo("ผู้ป่วยติดธุระ");
    }

    @Test
    void cancelIsAllowedAfterCheckIn() {
        Appointment appointment = scheduled();
        appointment.checkIn();
        appointment.cancel("ผู้ป่วยขอกลับก่อน");

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void rejectsCompleteBeforeCheckIn() {
        assertThatThrownBy(() -> scheduled().complete())
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("SCHEDULED");
    }

    @Test
    void rejectsSecondCheckIn() {
        Appointment appointment = scheduled();
        appointment.checkIn();

        assertThatThrownBy(appointment::checkIn)
                .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    void rejectsCancelAfterCompleted() {
        Appointment appointment = scheduled();
        appointment.checkIn();
        appointment.complete();

        assertThatThrownBy(() -> appointment.cancel("เปลี่ยนใจ"))
                .isInstanceOf(AppointmentConflictException.class)
                .hasMessageContaining("COMPLETED");
    }

    @Test
    void rejectsCheckInAfterCancel() {
        Appointment appointment = scheduled();
        appointment.cancel("ผู้ป่วยยกเลิก");

        assertThatThrownBy(appointment::checkIn)
                .isInstanceOf(AppointmentConflictException.class);
    }

    @Test
    void updatesTimestampOnTransition() throws Exception {
        Appointment appointment = scheduled();
        Instant before = appointment.getUpdatedAt();
        Thread.sleep(5);

        appointment.checkIn();

        assertThat(appointment.getUpdatedAt()).isAfter(before);
    }

    private Appointment scheduled() {
        Instant startAt = Instant.now().plus(Duration.ofDays(1));
        return new Appointment(null, null, null, null, startAt, startAt.plus(Duration.ofHours(1)), null);
    }
}