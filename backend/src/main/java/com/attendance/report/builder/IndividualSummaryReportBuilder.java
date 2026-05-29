package com.attendance.report.builder;

import com.attendance.organization.domain.Employee;
import com.attendance.report.domain.ReportType;
import com.attendance.report.service.ReportData;
import com.attendance.report.service.ReportParameters;
import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Individual Summary report — one row per employee with totals aggregated
 * across the whole window.
 */
@Component
public class IndividualSummaryReportBuilder implements ReportBuilder {

    private final ReportSupport support;
    private final DailyTimeCardRepository dailyTimeCardRepository;

    public IndividualSummaryReportBuilder(ReportSupport support,
                                          DailyTimeCardRepository dailyTimeCardRepository) {
        this.support = support;
        this.dailyTimeCardRepository = dailyTimeCardRepository;
    }

    @Override
    public ReportType type() {
        return ReportType.INDIVIDUAL_SUMMARY;
    }

    @Override
    public ReportData build(ReportParameters params) {
        ReportSupport.DateWindow window = support.requireWindow(params);
        ReportSupport.CustomFieldColumns custom = support.customFieldColumns(params.includeCustomFields());

        List<String> headers = new ArrayList<>(List.of(
                "Employee Code", "Last Name", "First Name",
                "Days Present", "Days Absent", "Days Leave",
                "Total Worked Minutes", "Total Overtime Minutes",
                "Total Break Minutes", "Total Late Minutes"));
        headers.addAll(custom.headers());

        List<List<String>> rows = new ArrayList<>();
        for (Employee e : support.resolveEmployees(params)) {
            List<DailyTimeCard> cards = dailyTimeCardRepository
                    .search(e.getId(), null, window.from(), window.to());
            int present = 0, absent = 0, leave = 0;
            long worked = 0, overtime = 0, breaks = 0, late = 0;
            for (DailyTimeCard c : cards) {
                if (c.getStatus() == DailyTimeCardStatus.PRESENT
                        || c.getStatus() == DailyTimeCardStatus.PARTIAL) {
                    present++;
                } else if (c.getStatus() == DailyTimeCardStatus.ABSENT) {
                    absent++;
                } else if (c.getStatus() == DailyTimeCardStatus.LEAVE) {
                    leave++;
                }
                worked += c.getWorkedMinutes();
                overtime += c.getOvertimeMinutes();
                breaks += c.getBreakMinutes();
                late += c.getLateMinutes();
            }
            List<String> row = new ArrayList<>(List.of(
                    DailyReportBuilder.nz(e.getEmployeeCode()),
                    DailyReportBuilder.nz(e.getLastName()),
                    DailyReportBuilder.nz(e.getFirstName()),
                    Integer.toString(present),
                    Integer.toString(absent),
                    Integer.toString(leave),
                    Long.toString(worked),
                    Long.toString(overtime),
                    Long.toString(breaks),
                    Long.toString(late)));
            row.addAll(custom.valuesFor(e.getId()));
            rows.add(row);
        }
        ReportSupport.applySort(headers, rows, params.sort());
        return new ReportData(headers, rows);
    }
}
