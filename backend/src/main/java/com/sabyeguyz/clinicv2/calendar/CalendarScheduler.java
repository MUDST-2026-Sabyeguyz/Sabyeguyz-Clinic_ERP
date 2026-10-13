package com.sabyeguyz.clinicv2.calendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the sync on a timer; absent entirely unless app.calendar.enabled=true. */
@Component
@ConditionalOnProperty(prefix = "app.calendar", name = "enabled", havingValue = "true")
public class CalendarScheduler {
    private static final Logger log = LoggerFactory.getLogger(CalendarScheduler.class);
    private final CalendarSyncService sync;

    public CalendarScheduler(CalendarSyncService sync) {
        this.sync = sync;
    }

    @Scheduled(initialDelayString = "${app.calendar.push-interval-ms:30000}", fixedDelayString = "${app.calendar.push-interval-ms:30000}")
    public void push() {
        try {
            sync.push();
        } catch (RuntimeException failure) {
            log.warn("Calendar push round failed: {}", failure.getMessage());
        }
    }

    @Scheduled(initialDelayString = "${app.calendar.pull-interval-ms:60000}", fixedDelayString = "${app.calendar.pull-interval-ms:60000}")
    public void pull() {
        try {
            sync.pull();
        } catch (RuntimeException failure) {
            log.warn("Calendar pull round failed: {}", failure.getMessage());
        }
    }
}
