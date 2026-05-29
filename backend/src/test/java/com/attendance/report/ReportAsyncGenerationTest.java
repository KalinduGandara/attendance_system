package com.attendance.report;

import com.attendance.report.domain.ReportJob;
import com.attendance.report.domain.ReportStatus;
import com.attendance.report.domain.ReportType;
import com.attendance.report.repository.ReportJobRepository;
import com.attendance.report.service.ReportGenerationService;
import com.attendance.report.service.ReportParameters;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real asynchronous path — {@code submit()} → {@code @Async} worker →
 * {@code @Transactional runReport()} — rather than calling {@code runReport}
 * directly like the golden test. This guards against the self-invocation trap
 * where the worker bypasses the transactional proxy and leaves the job stuck
 * {@code QUEUED} with no persisted status.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportAsyncGenerationTest {

    @Autowired ReportGenerationService reportService;
    @Autowired ReportJobRepository reportJobRepository;

    @Test
    void submit_runsAsynchronouslyAndPersistsDoneStatusWithFile() {
        ReportParameters params = new ReportParameters(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31),
                null, null, null, null, null, null);

        ReportJob queued = reportService.submit(ReportType.DAILY_SUMMARY, params, null);
        assertThat(queued.getStatus()).isEqualTo(ReportStatus.QUEUED);

        UUID id = queued.getId();
        ReportJob done = null;
        for (int i = 0; i < 100; i++) {  // up to ~10s
            done = reportJobRepository.findById(id).orElseThrow();
            if (done.getStatus() == ReportStatus.DONE || done.getStatus() == ReportStatus.FAILED) {
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(done).isNotNull();
        assertThat(done.getStatus())
                .as("async report should reach DONE; error=%s", done.getErrorMessage())
                .isEqualTo(ReportStatus.DONE);
        assertThat(done.getFilePath()).isNotBlank();
        assertThat(done.getRowCount()).isNotNull();
        assertThat(done.getCompletedAt()).isNotNull();
        assertThat(Files.exists(Path.of(done.getFilePath()))).isTrue();
    }
}
