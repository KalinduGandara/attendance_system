package com.attendance.report.builder;

import com.attendance.common.error.ApiException;
import com.attendance.exception.domain.ExceptionEvent;
import com.attendance.exception.domain.ExceptionStatus;
import com.attendance.exception.repository.ExceptionEventRepository;
import com.attendance.organization.domain.Employee;
import com.attendance.report.domain.ReportType;
import com.attendance.report.service.ReportData;
import com.attendance.report.service.ReportParameters;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Exception report — attendance exceptions by type/status in the window.
 */
@Component
public class ExceptionReportBuilder implements ReportBuilder {

    private final ReportSupport support;
    private final ExceptionEventRepository exceptionEventRepository;

    public ExceptionReportBuilder(ReportSupport support,
                                  ExceptionEventRepository exceptionEventRepository) {
        this.support = support;
        this.exceptionEventRepository = exceptionEventRepository;
    }

    @Override
    public ReportType type() {
        return ReportType.EXCEPTION;
    }

    @Override
    public ReportData build(ReportParameters params) {
        ReportSupport.DateWindow window = support.requireWindow(params);
        ReportSupport.CustomFieldColumns custom = support.customFieldColumns(params.includeCustomFields());
        ExceptionStatus status = parseStatus(params.status());

        List<Employee> employees = support.resolveEmployees(params);
        Map<UUID, Employee> byId = employees.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        Map<UUID, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < employees.size(); i++) {
            orderIndex.put(employees.get(i).getId(), i);
        }

        List<String> headers = new ArrayList<>(List.of(
                "Employee Code", "Last Name", "First Name", "Work Date",
                "Exception Type", "Severity", "Status", "Resolution Note"));
        headers.addAll(custom.headers());

        List<ExceptionEvent> events = exceptionEventRepository.search(null, status, window.from(), window.to())
                .stream()
                .filter(ev -> byId.containsKey(ev.getEmployeeId()))
                .sorted(Comparator
                        .comparingInt((ExceptionEvent ev) -> orderIndex.getOrDefault(ev.getEmployeeId(), Integer.MAX_VALUE))
                        .thenComparing(ExceptionEvent::getWorkDate)
                        .thenComparing(ExceptionEvent::getId))
                .toList();

        List<List<String>> rows = new ArrayList<>();
        for (ExceptionEvent ev : events) {
            Employee e = byId.get(ev.getEmployeeId());
            List<String> row = new ArrayList<>(List.of(
                    DailyReportBuilder.nz(e.getEmployeeCode()),
                    DailyReportBuilder.nz(e.getLastName()),
                    DailyReportBuilder.nz(e.getFirstName()),
                    ev.getWorkDate().toString(),
                    ev.getExceptionType().name(),
                    ev.getSeverity().name(),
                    ev.getStatus().name(),
                    DailyReportBuilder.nz(ev.getResolutionNote())));
            row.addAll(custom.valuesFor(e.getId()));
            rows.add(row);
        }
        ReportSupport.applySort(headers, rows, params.sort());
        return new ReportData(headers, rows);
    }

    private static ExceptionStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ExceptionStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Unknown exception status: " + raw);
        }
    }
}
