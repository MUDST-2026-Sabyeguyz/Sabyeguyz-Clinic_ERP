package com.sabyeguyz.clinicv2.calendar;

import org.junit.jupiter.api.Test;

/** Runs the real HTTP parser against localhost, not a Google account or production calendar. */
class HttpGoogleCalendarClientTest {
    @Test
    void explicitBusyScopeTimezoneAllDayAndOwnEventFiltering() throws Exception {
        HttpBusyBlocksCheck.main(new String[0]);
    }
}
