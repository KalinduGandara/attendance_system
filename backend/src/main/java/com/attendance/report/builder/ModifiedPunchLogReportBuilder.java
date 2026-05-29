package com.attendance.report.builder;

import com.attendance.identity.domain.User;
import com.attendance.identity.repository.UserRepository;
import com.attendance.organization.domain.Employee;
import com.attendance.report.domain.ReportType;
import com.attendance.report.service.ReportData;
import com.attendance.report.service.ReportParameters;
import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.TimeCardEdit;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.repository.TimeCardEditRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Modified Punch Log History report — every manual time-card edit in the
 * window, with the before/after snapshots and who made the change.
 */
@Component
public class ModifiedPunchLogReportBuilder implements ReportBuilder {

    private final ReportSupport support;
    private final TimeCardEditRepository timeCardEditRepository;
    private final DailyTimeCardRepository dailyTimeCardRepository;
    private final UserRepository userRepository;

    public ModifiedPunchLogReportBuilder(ReportSupport support,
                                         TimeCardEditRepository timeCardEditRepository,
                                         DailyTimeCardRepository dailyTimeCardRepository,
                                         UserRepository userRepository) {
        this.support = support;
        this.timeCardEditRepository = timeCardEditRepository;
        this.dailyTimeCardRepository = dailyTimeCardRepository;
        this.userRepository = userRepository;
    }

    @Override
    public ReportType type() {
        return ReportType.MODIFIED_PUNCH_LOG;
    }

    @Override
    public ReportData build(ReportParameters params) {
        ReportSupport.DateWindow window = support.requireWindow(params);
        Map<UUID, Employee> employeesById = support.resolveEmployees(params).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        List<TimeCardEdit> edits = timeCardEditRepository.findEditedBetween(
                window.fromInstantUtc(), window.toInstantExclusiveUtc());

        Map<UUID, DailyTimeCard> cardsById = dailyTimeCardRepository.findAllById(
                        edits.stream().map(TimeCardEdit::getDailyTimeCardId).distinct().toList())
                .stream().collect(Collectors.toMap(DailyTimeCard::getId, c -> c));
        Map<UUID, String> usernamesById = userRepository.findAllById(
                        edits.stream().map(TimeCardEdit::getEditedByUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));

        List<String> headers = List.of(
                "Edited At", "Employee Code", "Last Name", "First Name", "Work Date",
                "Change Type", "Reason", "Edited By", "Before", "After");

        List<List<String>> rows = new ArrayList<>();
        for (TimeCardEdit edit : edits) {
            DailyTimeCard card = cardsById.get(edit.getDailyTimeCardId());
            UUID employeeId = card == null ? null : card.getEmployeeId();
            Employee e = employeeId == null ? null : employeesById.get(employeeId);
            if (e == null) {
                continue;  // outside the requested employee scope
            }
            String editor = usernamesById.getOrDefault(edit.getEditedByUserId(),
                    edit.getEditedByUserId() == null ? "" : edit.getEditedByUserId().toString());
            rows.add(new ArrayList<>(List.of(
                    DailyReportBuilder.instant(edit.getEditedAt()),
                    DailyReportBuilder.nz(e.getEmployeeCode()),
                    DailyReportBuilder.nz(e.getLastName()),
                    DailyReportBuilder.nz(e.getFirstName()),
                    card.getWorkDate().toString(),
                    edit.getChangeType().name(),
                    DailyReportBuilder.nz(edit.getReason()),
                    editor,
                    DailyReportBuilder.nz(edit.getBeforeJson()),
                    DailyReportBuilder.nz(edit.getAfterJson()))));
        }
        ReportSupport.applySort(headers, rows, params.sort());
        return new ReportData(headers, rows);
    }
}
