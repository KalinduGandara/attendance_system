package com.attendance.platform.persistence;

/**
 * Connection coordinates for a database dump, parsed from the active datasource.
 * The {@code password} is carried separately so the dialect can build an argv
 * that never embeds the secret (it is passed via environment instead).
 */
public record BackupTarget(
        String host,
        int port,
        String database,
        String username,
        String password) {
}
