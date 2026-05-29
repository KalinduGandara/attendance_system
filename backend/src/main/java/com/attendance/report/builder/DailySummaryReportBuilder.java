package com.attendance.report.builder;

import com.attendance.organization.domain.Employee;
import com.attendance.report.domain.ReportType;
import com.attendance.report.service.ReportData;
import com.attendance.report.service.ReportParameters;
import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Daily Summary report — one row per (employee, day) with the computed totals
 * from that day's time card.
 */
@Component
public class DailySummaryReportBuilder implements ReportBuilder {

    private final ReportSupport support;
    private final DailyTimeCardRepository dailyTimeCardRepository;

    public DailySummaryReportBuilder(ReportSupport support,
                                     DailyTimeCardRepository dailyTimeCardRepository) {
        this.support = support;
        this.dailyTimeCardRepository = dailyTimeCardRepository;
    }

    @Override
    public ReportType type() {
        return ReportType.DAILY_SUMMARY;
    }

    @Override
    public ReportData build(ReportParameters params) {
        ReportSupport.DateWindow window = support.requireWindow(params);
        ReportSupport.CustomFieldColumns custom = support.customFieldColumns(params.includeCustomFields());

        List<String> headers = new ArrayList<>(List.of(
                "Employee Code", "Last Name", "First Name", "Date", "Status",
                "Worked Minutes", "Break Minutes", "Overtime Minutes",
                "Late Minutes", "Early Out Minutes"));
        headers.addAll(custom.headers());

        List<List<String>> rows = new ArrayList<>();
        for (Employee e : support.resolveEmployees(params)) {
            List<String> customValues = custom.valuesFor(e.getId());
            List<DailyTimeCard> cards = dailyTimeCardRepository
                    .search(e.getId(), null, window.from(), window.to());
            cards.stream()
                    .sorted(Comparator.comparing(DailyTimeCard::getWorkDate))
                    .forEach(c -> {
                        List<String> row = new ArrayList<>(List.of(
                                DailyReportBuilder.nz(e.getEmployeeCode()),
                                DailyReportBuilder.nz(e.getLastName()),
                                DailyReportBuilder.nz(e.getFirstName()),
                                c.getWorkDate().toString(),
                                c.getStatus().name(),
                                Integer.toString(c.getWorkedMinutes()),
                                Integer.toString(c.getBreakMinutes()),
                                Integer.toString(c.getOvertimeMinutes()),
                                Integer.toString(c.getLateMinutes()),
                                Integer.toString(c.getEarlyOutMinutes())));
                        row.addAll(customValues);
                        rows.add(row);
                    });
        }
        ReportSupport.applySort(headers, rows, params.sort());
        return new ReportData(headers, rows);
    }
}
