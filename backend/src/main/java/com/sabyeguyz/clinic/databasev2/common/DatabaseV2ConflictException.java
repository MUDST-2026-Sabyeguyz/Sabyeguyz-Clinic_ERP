package com.sabyeguyz.clinic.databasev2.common;

public class DatabaseV2ConflictException extends RuntimeException {
    public DatabaseV2ConflictException(String message) {
        super(message);
    }
}
