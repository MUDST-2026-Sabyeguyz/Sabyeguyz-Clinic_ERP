package com.sabyeguyz.clinic.databasev2.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Business-calendar dates are evaluated in the clinic's confirmed timezone. */
public final class DatabaseV2BusinessDates {
    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    private DatabaseV2BusinessDates() {}

    public static LocalDate today() {
        return today(Clock.systemUTC());
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
