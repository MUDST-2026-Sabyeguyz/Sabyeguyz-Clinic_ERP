package com.sabyeguyz.clinic.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(String message, Map<String, String> fields) {
    public ApiErrorResponse(String message) {
        this(message, null);
    }
}
