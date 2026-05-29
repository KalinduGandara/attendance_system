package com.attendance.admin.service;

import com.attendance.admin.domain.BackupJob;
import com.attendance.admin.domain.BackupStatus;
import com.attendance.admin.domain.BackupTrigger;
import com.attendance.admin.repository.BackupJobRepository;
import com.attendance.common.error.ApiException;
import com.attendance.platform.persistence.BackupTarget;
import com.attendance.platform.persistence.DatabaseDialectPort;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Orchestrates database backups. A run persists a {@link BackupJob} immediately
 * (so the Backups page shows it live) and executes the vendor dump command from
 * the {@link DatabaseDialectPort} on a worker thread; all state lives in the
 * row, so a run survives a restart. Successful runs trigger keep-count rotation.
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final Pattern JDBC_URL = Pattern.compile("jdbc:[^:]+://([^:/]+)(?::(\\d+))?/([^?;]+)");

    private final BackupJobRepository jobRepository;
    private final DatabaseDialectPort dialect;
    private final BackupExecutor executor;
    private final SystemSettingService settings;
    private final Environment env;
    private final ObjectProvider<BackupService> self;
    private final Path backupsDir;

    public BackupService(BackupJobRepository jobRepository,
                         DatabaseDialectPort dialect,
                         BackupExecutor executor,
                         SystemSettingService settings,
                         Environment env,
                         ObjectProvider<BackupService> self) {
        this.jobRepository = jobRepository;
        this.dialect = dialect;
        this.executor = executor;
        this.settings = settings;
        this.env = env;
        this.self = self;
        this.backupsDir = Path.of(env.getProperty("attendance.backups.dir", "backups"));
    }

    @PostConstruct
    void ensureDir() {
        try {
            Files.createDirectories(backupsDir);
        } catch (IOException ex) {
            log.warn("Could not create backups directory {}: {}", backupsDir, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<BackupJob> recent() {
        return jobRepository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public BackupJob get(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found", "Backup job not found"));
    }

    /** Queues a manual backup and runs it asynchronously. Returns the RUNNING job. */
    public BackupJob runManual() {
        BackupJob job = self.getObject().createJob(BackupTrigger.MANUAL);
        self.getObject().executeAsync(job.getId());
        return job;
    }

    /** Scheduler entry point: runs synchronously on the scheduler thread when enabled. */
    public void runScheduledIfEnabled() {
        if (!settings.getBoolean("backup_enabled", false)) {
            log.debug("Scheduled backup skipped: backup_enabled=false");
            return;
        }
        BackupJob job = self.getObject().createJob(BackupTrigger.SCHEDULED);
        self.getObject().execute(job.getId());
    }

    @Transactional
    public BackupJob createJob(BackupTrigger trigger) {
        BackupJob job = new BackupJob();
        job.setTriggerType(trigger);
        job.setStatus(BackupStatus.RUNNING);
        job.setStartedAt(Instant.now());
        return jobRepository.save(job);
    }

    @Async("backupExecutor")
    public void executeAsync(UUID jobId) {
        try {
            self.getObject().execute(jobId);
        } catch (RuntimeException ex) {
            log.error("Backup job {} failed", jobId, ex);
        }
    }

    @Transactional
    public void execute(UUID jobId) {
        BackupJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Backup job missing: " + jobId));
        try {
            BackupTarget target = resolveTarget();
            Path file = backupsDir.resolve("backup-" + jobId + "." + dialect.backupFileExtension());
            BackupExecutor.Result result = executor.run(
                    dialect.buildBackupCommand(target),
                    dialect.backupEnvironment(target),
                    file);
            if (result.success()) {
                job.setStatus(BackupStatus.DONE);
                job.setFilePath(file.toString());
                job.setSizeBytes(result.sizeBytes());
                rotateOldBackups();
            } else {
                job.setStatus(BackupStatus.FAILED);
                job.setErrorMessage(result.error());
                deleteQuietly(file);
            }
        } catch (RuntimeException ex) {
            log.error("Backup job {} failed", jobId, ex);
            job.setStatus(BackupStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
        } finally {
            job.setCompletedAt(Instant.now());
        }
    }

    @Transactional(readOnly = true)
    public Resource resolveDownload(UUID id) {
        BackupJob job = get(id);
        if (job.getStatus() != BackupStatus.DONE || job.getFilePath() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "conflict",
                    "Backup is not available for download (status " + job.getStatus() + ")");
        }
        Path file = Path.of(job.getFilePath());
        if (!Files.exists(file)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not-found", "Backup file is missing");
        }
        return new FileSystemResource(file);
    }

    public String fileExtension() {
        return dialect.backupFileExtension();
    }

    private BackupTarget resolveTarget() {
        String url = env.getProperty("spring.datasource.url", "");
        Matcher m = JDBC_URL.matcher(url);
        String host = "localhost";
        int port = 3306;
        String database = "attendance";
        if (m.find()) {
            host = m.group(1);
            if (m.group(2) != null) {
                port = Integer.parseInt(m.group(2));
            }
            database = m.group(3);
        }
        String username = env.getProperty("spring.datasource.username", "root");
        String password = env.getProperty("spring.datasource.password", "");
        return new BackupTarget(host, port, database, username, password);
    }

    /** Keeps only the newest {@code backup_keep_count} dump files (0 = keep all). */
    private void rotateOldBackups() {
        int keep = settings.getInt("backup_keep_count", 0);
        if (keep <= 0) {
            return;
        }
        String suffix = "." + dialect.backupFileExtension();
        try (Stream<Path> files = Files.list(backupsDir)) {
            List<Path> dumps = files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .sorted(Comparator.comparingLong(BackupService::lastModified).reversed())
                    .toList();
            for (int i = keep; i < dumps.size(); i++) {
                deleteQuietly(dumps.get(i));
            }
        } catch (IOException ex) {
            log.warn("Backup rotation failed: {}", ex.getMessage());
        }
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException ex) {
            return 0L;
        }
    }

    private void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ex) {
            log.warn("Could not delete backup file {}: {}", p, ex.getMessage());
        }
    }
}
