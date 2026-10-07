package com.sabyeguyz.clinic.databasev2.patient;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("database-v2")
public class DatabaseV2PatientClockConfiguration {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Bangkok");

    @Bean
    Clock patientBusinessClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
