package com.sabyeguyz.clinicv2.appointment;

import com.sabyeguyz.clinicv2.branch.Branch;
import com.sabyeguyz.clinicv2.calendar.CalendarProperties;
import com.sabyeguyz.clinicv2.calendar.GoogleCalendarClient;
import com.sabyeguyz.clinicv2.staff.Staff;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppointmentCalendarScopeTest {
    @SuppressWarnings("unchecked")
    @Test
    void therapistBusyDoesNotCloseOtherTherapistsButBranchBusyDoesAndBackToBackIsAllowed() {
        LocalDate day = LocalDate.now().plusDays(2);
        Branch branch = new Branch();
        ReflectionTestUtils.setField(branch, "id", 1L);
        branch.setGoogleCalendarId("scope-test-calendar");
        branch.setBookingOpenTime(LocalTime.of(9, 0));
        branch.setBookingCloseTime(LocalTime.of(12, 0));
        branch.setDefaultAppointmentMinutes(60);
        Staff first = new Staff();
        ReflectionTestUtils.setField(first, "id", 11L);
        Staff second = new Staff();
        ReflectionTestUtils.setField(second, "id", 12L);
        var repo = mock(AppointmentRepository.class);
        when(repo.activeOfStaffForInterval(anyLong(), any(), any(), any())).thenReturn(List.of());
        var google = mock(GoogleCalendarClient.class);
        when(google.busyBlocks(anyString(), any(), any())).thenReturn(List.of(
                new GoogleCalendarClient.BusyBlock(day.atTime(9, 0), day.atTime(10, 0), 11L),
                new GoogleCalendarClient.BusyBlock(day.atTime(10, 0), day.atTime(11, 0))));
        ObjectProvider<GoogleCalendarClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(google);
        CalendarProperties properties = new CalendarProperties();
        properties.setEnabled(true);
        var availability = new AppointmentAvailabilityService(repo, provider, properties);

        var firstSlots = availability.availability(branch, first, null, day, null).slots();
        var secondSlots = availability.availability(branch, second, null, day, null).slots();
        assertFalse(firstSlots.stream().filter(s -> s.time().equals("09:00")).findFirst().orElseThrow().available());
        assertTrue(secondSlots.stream().filter(s -> s.time().equals("09:00")).findFirst().orElseThrow().available());
        assertFalse(secondSlots.stream().filter(s -> s.time().equals("10:00")).findFirst().orElseThrow().available());
        assertTrue(firstSlots.stream().filter(s -> s.time().equals("11:00")).findFirst().orElseThrow().available());
        assertDoesNotThrow(() -> availability.requireNoGoogleBusy(branch, day.atTime(9, 0), day.atTime(10, 0), 12L));
        assertThrows(IllegalArgumentException.class,
                () -> availability.requireNoGoogleBusy(branch, day.atTime(9, 0), day.atTime(10, 0), 11L));
        assertThrows(IllegalArgumentException.class,
                () -> availability.requireNoGoogleBusy(branch, day.atTime(10, 0), day.atTime(11, 0), 12L));
    }
}
