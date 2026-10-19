package com.sabyeguyz.clinicv2.config;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The clinic works in Bangkok time whatever the zone of the server (a container is usually UTC): "today", the month a
 * treatment or a sale belongs to, and the month of a payment all follow this zone.
 */
public final class ClinicTime {
    public static final ZoneId ZONE = ZoneId.of("Asia/Bangkok");

    private ClinicTime() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static LocalDate dateOf(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }
}
