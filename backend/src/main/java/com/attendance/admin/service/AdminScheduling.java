package com.attendance.admin.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.TriggerContext;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;

/**
 * Registers the backup and retention jobs with cron expressions read from
 * {@code system_setting} on every cycle, so an admin editing {@code backup_cron}
 * / {@code retention_cron} on the Settings page changes the schedule without a
 * restart. The enabled flag is checked inside each job, so the schedule keeps
 * ticking (cheaply, as a no-op) while a feature is paused.
 *
 * <p>This is the @Scheduled-based stand-in plan.md §9 anticipated replacing with
 * Quartz; the dynamic {@link Trigger} keeps cron config in the database as the
 * plan requires.
 */
@Configuration
public class AdminScheduling implements SchedulingConfigurer {

    private static final String DEFAULT_BACKUP_CRON = "0 0 3 * * *";
    private static final String DEFAULT_RETENTION_CRON = "0 0 4 * * *";

    private final SystemSettingService settings;
    private final BackupService backupService;
    private final RetentionService retentionService;

    public AdminScheduling(SystemSettingService settings,
                           BackupService backupService,
                           RetentionService retentionService) {
        this.settings = settings;
        this.backupService = backupService;
        this.retentionService = retentionService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(backupService::runScheduledIfEnabled,
                dynamicCron("backup_cron", DEFAULT_BACKUP_CRON));
        registrar.addTriggerTask(retentionService::runScheduledIfEnabled,
                dynamicCron("retention_cron", DEFAULT_RETENTION_CRON));
    }

    private Trigger dynamicCron(String settingKey, String defaultCron) {
        return new Trigger() {
            @Override
            public Instant nextExecution(TriggerContext context) {
                String cron = settings.getCron(settingKey, defaultCron);
                return new CronTrigger(cron).nextExecution(context);
            }
        };
    }
}
