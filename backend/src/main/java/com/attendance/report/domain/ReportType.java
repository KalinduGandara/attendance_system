package com.attendance.report.domain;

/**
 * The seven standardized report types from SRS §3.6 / FR-6.1.
 */
public enum ReportType {
    DAILY,
    DAILY_SUMMARY,
    INDIVIDUAL,
    INDIVIDUAL_SUMMARY,
    LEAVE,
    EXCEPTION,
    MODIFIED_PUNCH_LOG
}
