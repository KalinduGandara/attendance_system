package com.attendance.platform.persistence;

import java.util.List;

/**
 * Encapsulates vendor-specific persistence behavior (ADR-0003). v1 only needs
 * the database-backup command; the port keeps the rest of the codebase free of
 * vendor assumptions and gives PostgreSQL / MSSQL a single place to slot in.
 */
public interface DatabaseDialectPort {

    /** Human-readable vendor label, e.g. {@code "MariaDB"}. */
    String name();

    /** File extension for a logical dump produced by {@link #buildBackupCommand}. */
    String backupFileExtension();

    /**
     * Builds the OS command (argv) that dumps {@code target}'s database to
     * standard output. The password is intentionally omitted from the argv —
     * the executor supplies it via an environment variable so it never appears
     * in the process table.
     */
    List<String> buildBackupCommand(BackupTarget target);

    /** Environment variables the executor must set for {@link #buildBackupCommand} to authenticate. */
    default java.util.Map<String, String> backupEnvironment(BackupTarget target) {
        return java.util.Map.of();
    }
}
