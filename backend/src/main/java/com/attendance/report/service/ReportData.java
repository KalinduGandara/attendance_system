package com.attendance.report.service;

import java.util.List;

/**
 * Tabular result of a report build: a header row plus data rows, every cell a
 * pre-formatted string. The exporter port turns this into the wire format.
 */
public record ReportData(List<String> headers, List<List<String>> rows) {
}
