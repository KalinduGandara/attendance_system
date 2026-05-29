package com.attendance.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Default {@link BackupExecutor}: spawns the command via {@link ProcessBuilder},
 * redirects its stdout to the dump file, captures stderr for diagnostics, and
 * enforces a timeout so a hung dump cannot wedge the backup pool.
 */
@Component
public class ProcessBackupExecutor implements BackupExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProcessBackupExecutor.class);
    private static final long TIMEOUT_MINUTES = 60;
    private static final int MAX_STDERR_CHARS = 800;

    @Override
    public Result run(List<String> command, Map<String, String> environment, Path outputFile) {
        try {
            Files.createDirectories(outputFile.getParent());
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().putAll(environment);
            pb.redirectOutput(outputFile.toFile());
            Process process = pb.start();

            String stderr = readBounded(process.getErrorStream());
            boolean finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return Result.failed("Backup timed out after " + TIMEOUT_MINUTES + " minutes");
            }
            int exit = process.exitValue();
            if (exit != 0) {
                return Result.failed("Backup command exited " + exit
                        + (stderr.isBlank() ? "" : ": " + stderr));
            }
            return Result.ok(Files.size(outputFile));
        } catch (IOException ex) {
            // Most commonly: the dump binary is not installed on this host.
            log.warn("Backup command failed to run: {}", ex.getMessage());
            return Result.failed("Could not run backup command: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Result.failed("Backup interrupted");
        }
    }

    private String readBounded(InputStream in) throws IOException {
        byte[] all = in.readAllBytes();
        String s = new String(all, StandardCharsets.UTF_8).trim();
        return s.length() > MAX_STDERR_CHARS ? s.substring(0, MAX_STDERR_CHARS) + "…" : s;
    }
}
