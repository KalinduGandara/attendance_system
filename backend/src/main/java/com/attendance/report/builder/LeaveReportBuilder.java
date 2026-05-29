package com.attendance.report.builder;

import com.attendance.common.error.ApiException;
import com.attendance.organization.domain.Employee;
import com.attendance.leave.domain.LeaveRequest;
import com.attendance.leave.domain.LeaveRequestStatus;
import com.attendance.leave.domain.LeaveType;
import com.attendance.leave.repository.LeaveRequestRepository;
import com.attendance.leave.repository.LeaveTypeRepository;
import com.attendance.report.domain.ReportType;
import com.attendance.report.service.ReportData;
import com.attendance.report.service.ReportParameters;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Leave report — approved/requested leave per employee in the window, with the
 * number of days each request consumes.
 */
@Component
public class LeaveReportBuilder implements ReportBuilder {

    private final ReportSupport support;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveReportBuilder(ReportSupport support,
                              LeaveRequestRepository leaveRequestRepository,
                              LeaveTypeRepository leaveTypeRepository) {
        this.support = support;
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveTypeRepository = leaveTypeRepository;
    }

    @Override
    public ReportType type() {
        return ReportType.LEAVE;
    }

    @Override
    public ReportData build(ReportParameters params) {
        ReportSupport.DateWindow window = support.requireWindow(params);
        ReportSupport.CustomFieldColumns custom = support.customFieldColumns(params.includeCustomFields());
        LeaveRequestStatus status = parseStatus(params.status());

        List<Employee> employees = support.resolveEmployees(params);
        Map<UUID, Employee> byId = employees.stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        Map<UUID, Integer> orderIndex = new HashMap<>();
        for (int i = 0; i < employees.size(); i++) {
            orderIndex.put(employees.get(i).getId(), i);
        }
        Map<UUID, String> typeNameById = leaveTypeRepository.findAll().stream()
                .collect(Collectors.toMap(LeaveType::getId, LeaveType::getName, (a, b) -> a));

        List<String> headers = new ArrayList<>(List.of(
                "Employee Code", "Last Name", "First Name", "Leave Type",
                "Start Date", "End Date", "Half Day", "Status", "Days",
                "Retroactive", "Reason"));
        headers.addAll(custom.headers());

        List<LeaveRequest> requests = leaveRequestRepository.search(null, status, window.from(), window.to())
                .stream()
                .filter(r -> byId.containsKey(r.getEmployeeId()))
                .sorted(Comparator
                        .comparingInt((LeaveRequest r) -> orderIndex.getOrDefault(r.getEmployeeId(), Integer.MAX_VALUE))
                        .thenComparing(LeaveRequest::getStartDate)
                        .thenComparing(LeaveRequest::getId))
                .toList();

        List<List<String>> rows = new ArrayList<>();
        for (LeaveRequest r : requests) {
            Employee e = byId.get(r.getEmployeeId());
            List<String> row = new ArrayList<>(List.of(
                    DailyReportBuilder.nz(e.getEmployeeCode()),
                    DailyReportBuilder.nz(e.getLastName()),
                    DailyReportBuilder.nz(e.getFirstName()),
                    typeNameById.getOrDefault(r.getLeaveTypeId(), ""),
                    r.getStartDate().toString(),
                    r.getEndDate().toString(),
                    Boolean.toString(r.isHalfDay()),
                    r.getStatus().name(),
                    ReportSupport.decimal(days(r)),
                    Boolean.toString(r.isRetroactive()),
                    DailyReportBuilder.nz(r.getReason())));
            row.addAll(custom.valuesFor(e.getId()));
            rows.add(row);
        }
        ReportSupport.applySort(headers, rows, params.sort());
        return new ReportData(headers, rows);
    }

    private static BigDecimal days(LeaveRequest r) {
        if (r.isHalfDay()) {
            return new BigDecimal("0.5");
        }
        long inclusive = ChronoUnit.DAYS.between(r.getStartDate(), r.getEndDate()) + 1;
        return BigDecimal.valueOf(inclusive);
    }

    private static LeaveRequestStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LeaveRequestStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Unknown leave status: " + raw);
        }
    }
}
