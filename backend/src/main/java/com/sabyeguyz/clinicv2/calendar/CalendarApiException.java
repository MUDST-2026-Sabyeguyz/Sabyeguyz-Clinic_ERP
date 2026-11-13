package com.sabyeguyz.clinicv2.calendar;

/** A Google Calendar (or token) call failed; the status is the HTTP status, 0 when there was no response. */
public class CalendarApiException extends RuntimeException {
    private final int status;

    public CalendarApiException(
            int status,
            String message) {
        super(message);
        this.status = status;
    }

    public CalendarApiException(
            String message,
            Throwable cause) {
        super(message, cause);
        this.status = 0;
    }

    public int getStatus() {
        return status;
    }
}
