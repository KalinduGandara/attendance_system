package com.attendance.exception.domain;

public enum ExceptionType {
    MISSING_PUNCH_IN,
    MISSING_PUNCH_OUT,
    LATE_IN,
    EARLY_OUT,
    ABSENT_NO_LEAVE,
    UNAUTHORIZED_OT,
    ORPHAN_PUNCH
}
