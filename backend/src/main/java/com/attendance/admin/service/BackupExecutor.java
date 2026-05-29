package com.attendance.admin.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Runs an external backup command. Extracted behind an interface so backup
 * orchestration ({@link BackupService}) can be unit-tested without a real
 * {@code mysqldump} binary or database.
 */
public interface BackupExecutor {

    record Result(boolean success, long sizeBytes, String error) {
        public static Result ok(long sizeBytes) {
            return new Result(true, sizeBytes, null);
        }

        public static Result failed(String error) {
            return new Result(false, 0L, error);
        }
    }

    /**
     * Executes {@code command} with the extra {@code environment} entries set,
     * streaming its standard output to {@code outputFile}.
     */
    Result run(List<String> command, Map<String, String> environment, Path outputFile);
}
