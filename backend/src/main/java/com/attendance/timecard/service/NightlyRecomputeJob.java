package com.attendance.timecard.service;

import com.attendance.organization.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Nightly job that recomputes yesterday's time card for every active employee.
 *
 * <p>plan.md §5.3 calls for Quartz; @Scheduled is a stand-in until the Phase 9
 * backup job adds Quartz tables. The shape of this class is intentionally
 * thin so a future swap to Quartz is a one-class change with no caller churn.
 */
@Component
@EnableScheduling
public class NightlyRecomputeJob {

    private static final Logger log = LoggerFactory.getLogger(NightlyRecomputeJob.class);

    private final EmployeeRepository employeeRepository;
    private final TimeCardRecomputeService recomputeService;

    public NightlyRecomputeJob(EmployeeRepository employeeRepository,
                               TimeCardRecomputeService recomputeService) {
        this.employeeRepository = employeeRepository;
        this.recomputeService = recomputeService;
    }

    @Scheduled(cron = "${attendance.recompute.nightly-cron:0 30 2 * * *}")
    public void runNightly() {
        runForDate(LocalDate.now().minusDays(1));
    }

    /**
     * Visible-for-tests entry point. Recomputes the given date for every
     * active employee, swallowing per-employee failures so one bad row does
     * not stop the rest.
     */
    public int runForDate(LocalDate workDate) {
        List<UUID> employeeIds = employeeRepository.findActiveEmployeeIds();
        int ok = 0;
        for (UUID id : employeeIds) {
            try {
                recomputeService.recompute(id, workDate);
                ok++;
            } catch (RuntimeException ex) {
                log.warn("Nightly recompute failed for employee={} date={}", id, workDate, ex);
            }
        }
        log.info("Nightly recompute for {} completed: {}/{} employees", workDate, ok, employeeIds.size());
        return ok;
    }
}
