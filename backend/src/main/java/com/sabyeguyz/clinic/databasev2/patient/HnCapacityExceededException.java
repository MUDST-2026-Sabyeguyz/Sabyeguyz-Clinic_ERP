package com.sabyeguyz.clinic.databasev2.patient;

/** Expected domain failure when a branch/month HN sequence reaches its fixed capacity. */
public final class HnCapacityExceededException extends RuntimeException {
    public HnCapacityExceededException(String message) {
        super(message);
    }

    public HnCapacityExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
