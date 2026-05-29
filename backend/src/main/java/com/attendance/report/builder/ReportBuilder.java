package com.attendance.report.builder;

import com.attendance.report.domain.ReportType;
import com.attendance.report.service.ReportData;
import com.attendance.report.service.ReportParameters;

/**
 * Builds one {@link ReportType} into tabular {@link ReportData}. Implementations
 * are stateless Spring beans discovered by {@code ReportGenerationService}.
 */
public interface ReportBuilder {

    ReportType type();

    ReportData build(ReportParameters params);
}
