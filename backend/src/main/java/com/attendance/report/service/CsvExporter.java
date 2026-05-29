package com.attendance.report.service;

import com.attendance.common.csv.CsvWriter;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

@Component
public class CsvExporter implements ReportExporterPort {

    @Override
    public MediaType supportedMediaType() {
        return new MediaType("text", "csv", StandardCharsets.UTF_8);
    }

    @Override
    public String fileExtension() {
        return "csv";
    }

    @Override
    public void export(ReportData data, OutputStream out) {
        Writer writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        try {
            CsvWriter.write(writer, data.headers(), data.rows());
            writer.flush();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to write CSV report", ex);
        }
    }
}
