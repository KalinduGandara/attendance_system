package com.attendance.platform.persistence;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MariaDB / MySQL dialect: dumps via {@code mysqldump}. A consistent snapshot is
 * taken with {@code --single-transaction}; routines and triggers are included so
 * the dump restores the full schema. The password is passed through the
 * {@code MYSQL_PWD} environment variable rather than the command line.
 */
@Component
public class MariaDbDialect implements DatabaseDialectPort {

    @Override
    public String name() {
        return "MariaDB";
    }

    @Override
    public String backupFileExtension() {
        return "sql";
    }

    @Override
    public List<String> buildBackupCommand(BackupTarget target) {
        List<String> cmd = new ArrayList<>();
        cmd.add("mysqldump");
        cmd.add("--host=" + target.host());
        cmd.add("--port=" + target.port());
        cmd.add("--user=" + target.username());
        cmd.add("--single-transaction");
        cmd.add("--routines");
        cmd.add("--triggers");
        cmd.add("--databases");
        cmd.add(target.database());
        return cmd;
    }

    @Override
    public Map<String, String> backupEnvironment(BackupTarget target) {
        if (target.password() == null) {
            return Map.of();
        }
        return Map.of("MYSQL_PWD", target.password());
    }
}
