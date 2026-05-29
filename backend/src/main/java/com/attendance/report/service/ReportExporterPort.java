package com.attendance.report.service;

import org.springframework.http.MediaType;

import java.io.OutputStream;

/**
 * Output adapter for a built {@link ReportData}. v1 ships {@code CsvExporter};
 * PDF/XLSX exporters can be added later without touching the report builders.
 */
public interface ReportExporterPort {

    MediaType supportedMediaType();

    String fileExtension();

    void export(ReportData data, OutputStream out);
}
