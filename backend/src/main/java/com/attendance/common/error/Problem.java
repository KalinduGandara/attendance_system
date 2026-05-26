package com.attendance.common.error;

import java.util.List;

public record Problem(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String requestId,
        List<FieldError> errors
) {
    public record FieldError(String field, String code, String message) {
    }
}
