package com.attendance.report.builder;

import com.attendance.organization.domain.Employee;
import com.attendance.report.domain.ReportType;
import com.attendance.report.service.ReportData;
import com.attendance.report.service.ReportParameters;
import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.repository.PunchEventRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Daily report — one row per punch, per employee, within the window. The raw
 * punch-level trail behind every computed time card.
 */
@Component
public class DailyReportBuilder implements ReportBuilder {

    private final ReportSupport support;
    private final PunchEventRepository punchEventRepository;

    public DailyReportBuilder(ReportSupport support, PunchEventRepository punchEventRepository) {
        this.support = support;
        this.punchEventRepository = punchEventRepository;
    }

    @Override
    public ReportType type() {
        return ReportType.DAILY;
    }

    @Override
    public ReportData build(ReportParameters params) {
        ReportSupport.DateWindow window = support.requireWindow(params);
        ReportSupport.CustomFieldColumns custom = support.customFieldColumns(params.includeCustomFields());

        List<String> headers = new ArrayList<>(List.of(
                "Employee Code", "Last Name", "First Name",
                "Date", "Time (UTC)", "Punch Type", "Status"));
        headers.addAll(custom.headers());

        List<List<String>> rows = new ArrayList<>();
        for (Employee e : support.resolveEmployees(params)) {
            List<String> customValues = custom.valuesFor(e.getId());
            List<PunchEvent> punches = punchEventRepository.findForEmployeeBetween(
                    e.getId(), window.fromInstantUtc(), window.toInstantExclusiveUtc());
            for (PunchEvent p : punches) {
                List<String> row = new ArrayList<>(List.of(
                        nz(e.getEmployeeCode()),
                        nz(e.getLastName()),
                        nz(e.getFirstName()),
                        p.getEventTimeUtc().atZone(ZoneOffset.UTC).toLocalDate().toString(),
                        instant(p.getEventTimeUtc()),
                        p.getEventType().name(),
                        p.getStatus().name()));
                row.addAll(customValues);
                rows.add(row);
            }
        }
        ReportSupport.applySort(headers, rows, params.sort());
        return new ReportData(headers, rows);
    }

    static String nz(String s) {
        return s == null ? "" : s;
    }

    static String instant(Instant i) {
        return i == null ? "" : i.toString();
    }
}
