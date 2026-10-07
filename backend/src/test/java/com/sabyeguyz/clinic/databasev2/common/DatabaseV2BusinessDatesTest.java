package com.sabyeguyz.clinic.databasev2.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DatabaseV2BusinessDatesTest {
    @Test
    void switchesBusinessDateAtBangkokMidnightRegardlessOfClockZone() {
        Clock beforeMidnightUtc = Clock.fixed(Instant.parse("2026-10-08T16:59:59Z"), ZoneOffset.UTC);
        Clock atMidnightUtc = Clock.fixed(Instant.parse("2026-10-08T17:00:00Z"), ZoneOffset.UTC);

        assertThat(DatabaseV2BusinessDates.today(beforeMidnightUtc)).hasToString("2026-10-08");
        assertThat(DatabaseV2BusinessDates.today(atMidnightUtc)).hasToString("2026-10-09");
    }
}
