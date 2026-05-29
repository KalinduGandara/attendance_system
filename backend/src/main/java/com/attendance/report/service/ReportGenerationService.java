package com.attendance.report.service;

import com.attendance.common.error.ApiException;
import com.attendance.report.builder.ReportBuilder;
import com.attendance.report.domain.ReportJob;
import com.attendance.report.domain.ReportStatus;
import com.attendance.report.domain.ReportType;
import com.attendance.report.repository.ReportJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Submits, runs, and serves report jobs. Generation is asynchronous: the POST
 * persists a {@code QUEUED} job and returns immediately; a worker thread runs
 * the matching {@link ReportBuilder}, writes the CSV under the configured
 * reports directory, and flips the job to {@code DONE}/{@code FAILED}. Because
 * all state lives in {@code report_job}, a job survives a backend restart.
 */
@Service
public class ReportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationService.class);

    private final ReportJobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final ReportExporterPort exporter;
    private final ObjectProvider<ReportGenerationService> self;
    private final Map<ReportType, ReportBuilder> builders = new EnumMap<>(ReportType.class);
    private final Path reportsDir;

    public ReportGenerationService(ReportJobRepository jobRepository,
                                   ObjectMapper objectMapper,
                                   ReportExporterPort exporter,
                                   List<ReportBuilder> builderBeans,
                                   ObjectProvider<ReportGenerationService> self,
                                   org.springframework.core.env.Environment env) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.exporter = exporter;
        this.self = self;
        for (ReportBuilder b : builderBeans) {
            builders.put(b.type(), b);
        }
        this.reportsDir = Path.of(env.getProperty("attendance.reports.dir", "reports"));
    }

    @PostConstruct
    void ensureDir() {
        try {
            Files.createDirectories(reportsDir);
        } catch (IOException ex) {
            log.warn("Could not create reports directory {}: {}", reportsDir, ex.getMessage());
        }
    }

    /** Persists a {@code QUEUED} job. Does not start generation. */
    @Transactional
    public ReportJob queue(ReportType type, ReportParameters params, UUID requestedBy) {
        if (!builders.containsKey(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Unsupported report type: " + type);
        }
        ReportJob job = new ReportJob();
        job.setReportType(type);
        job.setParametersJson(writeParams(params));
        job.setRequestedBy(requestedBy);
        job.setStatus(ReportStatus.QUEUED);
        return jobRepository.save(job);
    }

    /** Queues a job and kicks off asynchronous generation. */
    public ReportJob submit(ReportType type, ReportParameters params, UUID requestedBy) {
        ReportJob job = queue(type, params, requestedBy);
        self.getObject().runReportAsync(job.getId());
        return job;
    }

    @Async("reportExecutor")
    public void runReportAsync(UUID jobId) {
        try {
            runReport(jobId);
        } catch (RuntimeException ex) {
            log.error("Report job {} failed", jobId, ex);
        }
    }

    @Transactional
    public void runReport(UUID jobId) {
        ReportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Report job missing: " + jobId));
        job.setStatus(ReportStatus.RUNNING);
        job.setStartedAt(Instant.now());
        try {
            ReportParameters params = readParams(job.getParametersJson());
            ReportBuilder builder = builders.get(job.getReportType());
            ReportData data = builder.build(params);

            Path file = reportsDir.resolve(jobId + "." + exporter.fileExtension());
            Files.createDirectories(reportsDir);
            try (OutputStream out = Files.newOutputStream(file)) {
                exporter.export(data, out);
            }
            job.setFilePath(file.toString());
            job.setRowCount((long) data.rows().size());
            job.setStatus(ReportStatus.DONE);
        } catch (ApiException ex) {
            job.setStatus(ReportStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
        } catch (Exception ex) {
            log.error("Report generation failed for job {}", jobId, ex);
            job.setStatus(ReportStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
        } finally {
            job.setCompletedAt(Instant.now());
        }
    }

    @Transactional(readOnly = true)
    public ReportJob get(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "Report job not found"));
    }

    @Transactional(readOnly = true)
    public List<ReportJob> recent(UUID requestedBy) {
        return requestedBy == null
                ? jobRepository.findTop50ByOrderByCreatedAtDesc()
                : jobRepository.findTop50ByRequestedByOrderByCreatedAtDesc(requestedBy);
    }

    /** Resolves the generated file for download, validating the job is complete. */
    @Transactional(readOnly = true)
    public Resource resolveDownload(UUID id) {
        ReportJob job = get(id);
        if (job.getStatus() != ReportStatus.DONE || job.getFilePath() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Report is not ready for download (status " + job.getStatus() + ")");
        }
        Path file = Path.of(job.getFilePath());
        if (!Files.exists(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not-found", "Report file is missing");
        }
        return new FileSystemResource(file);
    }

    public MediaTypeAndExtension exportMeta() {
        return new MediaTypeAndExtension(exporter.supportedMediaType().toString(), exporter.fileExtension());
    }

    public record MediaTypeAndExtension(String mediaType, String extension) {
    }

    private String writeParams(ReportParameters params) {
        try {
            return objectMapper.writeValueAsString(params == null ? new ReportParameters(
                    null, null, null, null, null, null, null, null) : params);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Invalid report parameters: " + ex.getMessage());
        }
    }

    private ReportParameters readParams(String json) {
        try {
            return objectMapper.readValue(json, ReportParameters.class);
        } catch (JsonProcessingException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Invalid report parameters: " + ex.getMessage());
        }
    }
}
