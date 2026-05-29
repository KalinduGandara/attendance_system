package com.attendance.platform.persistence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MariaDbDialectTest {

    private final MariaDbDialect dialect = new MariaDbDialect();

    @Test
    void buildsMysqldumpCommandWithoutEmbeddingThePassword() {
        BackupTarget target = new BackupTarget("db.internal", 3307, "attendance", "backup_user", "s3cr3t");

        List<String> cmd = dialect.buildBackupCommand(target);

        assertThat(cmd).startsWith("mysqldump");
        assertThat(cmd).contains("--host=db.internal", "--port=3307", "--user=backup_user",
                "--single-transaction", "--databases", "attendance");
        // The secret must never appear in the argv (it would be visible in `ps`).
        assertThat(cmd).noneMatch(arg -> arg.contains("s3cr3t"));
    }

    @Test
    void passesPasswordViaEnvironment() {
        BackupTarget target = new BackupTarget("localhost", 3306, "attendance", "root", "p@ss");

        assertThat(dialect.backupEnvironment(target)).containsEntry("MYSQL_PWD", "p@ss");
        assertThat(dialect.backupFileExtension()).isEqualTo("sql");
        assertThat(dialect.name()).isEqualTo("MariaDB");
    }
}
