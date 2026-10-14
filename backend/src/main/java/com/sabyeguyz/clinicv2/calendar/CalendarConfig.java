package com.sabyeguyz.clinicv2.calendar;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties(CalendarProperties.class)
@EnableScheduling
public class CalendarConfig {
}
