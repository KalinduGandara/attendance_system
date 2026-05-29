package com.attendance.admin;

import com.attendance.admin.domain.BackupJob;
import com.attendance.admin.domain.BackupStatus;
import com.attendance.admin.domain.BackupTrigger;
import com.attendance.admin.service.BackupExecutor;
import com.attendance.admin.service.BackupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Drives backup orchestration without a real {@code mysqldump}: the
 * {@link BackupExecutor} is mocked so we can assert the job lifecycle, the
 * recorded file/size, and the failure path.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackupServiceTest {

    @Autowired BackupService backupService;
    @MockBean BackupExecutor executor;

    @Test
    void successful_backup_records_done_with_file_and_size() {
        when(executor.run(any(), any(), any())).thenAnswer(inv -> {
            Path target = inv.getArgument(2);
            Files.createDirectories(target.getParent());
            Files.writeString(target, "-- dummy dump\n");
            return BackupExecutor.Result.ok(Files.size(target));
        });

        BackupJob job = backupService.createJob(BackupTrigger.MANUAL);
        backupService.execute(job.getId());

        BackupJob done = backupService.get(job.getId());
        assertThat(done.getStatus()).isEqualTo(BackupStatus.DONE);
        assertThat(done.getFilePath()).isNotBlank();
        assertThat(done.getSizeBytes()).isPositive();
        assertThat(done.getCompletedAt()).isNotNull();
        assertThat(Files.exists(Path.of(done.getFilePath()))).isTrue();

        assertThat(backupService.resolveDownload(done.getId())).isNotNull();
    }

    @Test
    void failed_backup_records_failure_with_message() {
        when(executor.run(any(), any(), any()))
                .thenReturn(BackupExecutor.Result.failed("mysqldump: command not found"));

        BackupJob job = backupService.createJob(BackupTrigger.SCHEDULED);
        backupService.execute(job.getId());

        BackupJob failed = backupService.get(job.getId());
        assertThat(failed.getStatus()).isEqualTo(BackupStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("command not found");
        assertThat(failed.getCompletedAt()).isNotNull();
    }

    @Test
    void executor_receives_a_mysqldump_command() {
        when(executor.run(any(), any(), any())).thenAnswer(inv -> {
            List<String> command = inv.getArgument(0);
            Map<String, String> env = inv.getArgument(1);
            assertThat(command).isNotEmpty();
            assertThat(command.get(0)).isEqualTo("mysqldump");
            // env is provided by the dialect (may be empty when no password configured)
            assertThat(env).isNotNull();
            Path target = inv.getArgument(2);
            Files.createDirectories(target.getParent());
            Files.writeString(target, "x");
            return BackupExecutor.Result.ok(1);
        });

        UUID id = backupService.createJob(BackupTrigger.MANUAL).getId();
        backupService.execute(id);

        assertThat(backupService.get(id).getStatus()).isEqualTo(BackupStatus.DONE);
    }
}
