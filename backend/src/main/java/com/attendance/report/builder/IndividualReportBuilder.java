package com.attendance.report.builder;

import com.attendance.common.error.ApiException;
import com.attendance.organization.domain.Employee;
import com.attendance.report.domain.ReportType;
import com.attendance.report.service.ReportData;
import com.attendance.report.service.ReportParameters;
import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.TimeCardBreakdown;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.repository.TimeCodeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Individual report — full per-day detail for one employee over the window,
 * exploded to one row per time-card breakdown line.
 */
@Component
public class IndividualReportBuilder implements ReportBuilder {

    private final ReportSupport support;
    private final DailyTimeCardRepository dailyTimeCardRepository;
    private final TimeCodeRepository timeCodeRepository;

    public IndividualReportBuilder(ReportSupport support,
                                   DailyTimeCardRepository dailyTimeCardRepository,
                                   TimeCodeRepository timeCodeRepository) {
        this.support = support;
        this.dailyTimeCardRepository = dailyTimeCardRepository;
        this.timeCodeRepository = timeCodeRepository;
    }

    @Override
    public ReportType type() {
        return ReportType.INDIVIDUAL;
    }

    @Override
    public ReportData build(ReportParameters params) {
        ReportSupport.DateWindow window = support.requireWindow(params);
        if (params.employeeId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Individual report requires 'employeeId'");
        }
        Employee e = support.requireEmployee(params.employeeId());
        ReportSupport.CustomFieldColumns custom = support.customFieldColumns(params.includeCustomFields());
        Map<UUID, String> codeById = timeCodeRepository.findAll().stream()
                .collect(Collectors.toMap(TimeCode::getId, TimeCode::getCode, (a, b) -> a));

        List<String> headers = new ArrayList<>(List.of(
                "Employee Code", "Last Name", "First Name", "Date", "Status",
                "Scheduled Start (UTC)", "Scheduled End (UTC)",
                "Actual Start (UTC)", "Actual End (UTC)",
                "Worked Minutes", "Break Minutes", "Overtime Minutes",
                "Time Code", "Breakdown Minutes", "Rated Minutes"));
        headers.addAll(custom.headers());

        List<String> customValues = custom.valuesFor(e.getId());
        List<List<String>> rows = new ArrayList<>();
        List<DailyTimeCard> cards = dailyTimeCardRepository
                .search(e.getId(), null, window.from(), window.to());
        cards.stream()
                .sorted(Comparator.comparing(DailyTimeCard::getWorkDate))
                .forEach(c -> {
                    List<TimeCardBreakdown> breakdowns = c.getBreakdowns();
                    if (breakdowns.isEmpty()) {
                        rows.add(buildRow(e, c, null, codeById, customValues));
                    } else {
                        breakdowns.stream()
                                .sorted(Comparator.comparingInt(TimeCardBreakdown::getSequenceOrder))
                                .forEach(b -> rows.add(buildRow(e, c, b, codeById, customValues)));
                    }
                });
        ReportSupport.applySort(headers, rows, params.sort());
        return new ReportData(headers, rows);
    }

    private List<String> buildRow(Employee e, DailyTimeCard c, TimeCardBreakdown b,
                                  Map<UUID, String> codeById, List<String> customValues) {
        List<String> row = new ArrayList<>(List.of(
                DailyReportBuilder.nz(e.getEmployeeCode()),
                DailyReportBuilder.nz(e.getLastName()),
                DailyReportBuilder.nz(e.getFirstName()),
                c.getWorkDate().toString(),
                c.getStatus().name(),
                DailyReportBuilder.instant(c.getScheduledStartUtc()),
                DailyReportBuilder.instant(c.getScheduledEndUtc()),
                DailyReportBuilder.instant(c.getActualStartUtc()),
                DailyReportBuilder.instant(c.getActualEndUtc()),
                Integer.toString(c.getWorkedMinutes()),
                Integer.toString(c.getBreakMinutes()),
                Integer.toString(c.getOvertimeMinutes()),
                b == null ? "" : codeById.getOrDefault(b.getTimeCodeId(), ""),
                b == null ? "" : Integer.toString(b.getMinutes()),
                b == null ? "" : Integer.toString(b.getRatedMinutes())));
        row.addAll(customValues);
        return row;
    }
}
