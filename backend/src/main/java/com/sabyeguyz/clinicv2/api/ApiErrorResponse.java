package com.sabyeguyz.clinicv2.api;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        String message,
        Map<String, String> fields) {
    public ApiErrorResponse(String message) {
        this(message, null);
    }
}
