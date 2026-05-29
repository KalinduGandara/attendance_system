package com.attendance.report.service;

import com.attendance.common.retention.BatchPurger;
import com.attendance.common.retention.RetentionPort;
import com.attendance.report.domain.ReportJob;
import com.attendance.report.repository.ReportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/** Retention purge for {@code report_job} rows; also removes the generated CSV. */
@Component
public class ReportJobRetentionAdapter implements RetentionPort {

    private static final Logger log = LoggerFactory.getLogger(ReportJobRetentionAdapter.class);

    private final ReportJobRepository repository;

    public ReportJobRetentionAdapter(ReportJobRepository repository) {
        this.repository = repository;
    }

    @Override
    public String entityType() {
        return "report_job";
    }

    @Override
    @Transactional
    public long purgeOlderThan(Instant cutoff, int batchSize, int maxBatches) {
        return BatchPurger.purge(batchSize, maxBatches,
                page -> repository.findIdsOlderThan(cutoff, page),
                ids -> {
                    for (ReportJob job : repository.findAllById(ids)) {
                        deleteFileQuietly(job.getFilePath());
                    }
                    repository.deleteAllByIdInBatch(ids);
                });
    }

    private void deleteFileQuietly(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not delete report file {} during retention purge: {}", filePath, ex.getMessage());
        }
    }
}
