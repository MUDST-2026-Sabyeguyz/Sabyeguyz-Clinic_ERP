package com.sabyeguyz.clinicv2.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ClinicTimeTest {
    @ParameterizedTest
    @CsvSource({
        "2026-09-30T16:59:59Z, 2026-09-30",
        "2026-09-30T17:00:00Z, 2026-10-01",
        "2026-12-31T16:59:59Z, 2026-12-31",
        "2026-12-31T17:00:00Z, 2027-01-01",
        "2028-02-28T17:00:00Z, 2028-02-29",
        "2028-02-29T17:00:00Z, 2028-03-01"
    })
    void usesBangkokDateAcrossMonthYearAndLeapDay(String instant, String date) {
        assertEquals(LocalDate.parse(date), ClinicTime.dateOf(Instant.parse(instant)));
    }
}
