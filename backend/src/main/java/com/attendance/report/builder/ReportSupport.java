package com.attendance.report.builder;

import com.attendance.common.error.ApiException;
import com.attendance.organization.domain.CustomFieldDefinition;
import com.attendance.organization.domain.CustomFieldEntityType;
import com.attendance.organization.domain.CustomFieldType;
import com.attendance.organization.domain.CustomFieldValue;
import com.attendance.organization.domain.Employee;
import com.attendance.organization.repository.CustomFieldDefinitionRepository;
import com.attendance.organization.repository.CustomFieldValueRepository;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.report.service.ReportParameters;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Shared helpers for report builders: resolving the target employee set,
 * appending selectable custom-field columns, and small formatting utilities.
 */
@Component
public class ReportSupport {

    /** Employees sort deterministically by last name, first name, then code. */
    static final Comparator<Employee> EMPLOYEE_ORDER = Comparator
            .comparing(Employee::getLastName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Employee::getFirstName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Employee::getEmployeeCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final EmployeeRepository employeeRepository;
    private final CustomFieldDefinitionRepository customFieldDefinitionRepository;
    private final CustomFieldValueRepository customFieldValueRepository;

    public ReportSupport(EmployeeRepository employeeRepository,
                         CustomFieldDefinitionRepository customFieldDefinitionRepository,
                         CustomFieldValueRepository customFieldValueRepository) {
        this.employeeRepository = employeeRepository;
        this.customFieldDefinitionRepository = customFieldDefinitionRepository;
        this.customFieldValueRepository = customFieldValueRepository;
    }

    /**
     * Resolves the employees in scope: a single employee, a group, a department,
     * or — if no narrower filter is set — everyone. Always returned in the
     * deterministic {@link #EMPLOYEE_ORDER}.
     */
    public List<Employee> resolveEmployees(ReportParameters params) {
        List<Employee> employees;
        if (params.employeeId() != null) {
            employees = employeeRepository.findById(params.employeeId())
                    .map(List::of)
                    .orElse(List.of());
        } else if (params.groupId() != null) {
            employees = employeeRepository.findByGroupId(params.groupId());
        } else if (params.departmentId() != null) {
            employees = employeeRepository.findByDepartmentId(params.departmentId());
        } else {
            employees = employeeRepository.findAll();
        }
        return employees.stream().sorted(EMPLOYEE_ORDER).toList();
    }

    public Employee requireEmployee(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "validation",
                        "Unknown employee: " + employeeId));
    }

    /**
     * Resolves the requested employee custom-field keys (preserving request
     * order, skipping unknown keys) into column definitions for appending.
     */
    public CustomFieldColumns customFieldColumns(List<String> keys) {
        List<CustomFieldDefinition> defs = new ArrayList<>();
        for (String key : keys) {
            customFieldDefinitionRepository
                    .findByEntityTypeAndFieldKey(CustomFieldEntityType.EMPLOYEE, key)
                    .ifPresent(defs::add);
        }
        return new CustomFieldColumns(defs);
    }

    /** A resolved set of custom-field columns to append per employee. */
    public final class CustomFieldColumns {
        private final List<CustomFieldDefinition> defs;

        private CustomFieldColumns(List<CustomFieldDefinition> defs) {
            this.defs = defs;
        }

        public List<String> headers() {
            return defs.stream().map(CustomFieldDefinition::getDisplayLabel).toList();
        }

        public List<String> valuesFor(UUID employeeId) {
            List<String> out = new ArrayList<>(defs.size());
            for (CustomFieldDefinition def : defs) {
                out.add(customFieldValueRepository
                        .findByDefinitionIdAndEntityId(def.getId(), employeeId)
                        .map(v -> format(def.getFieldType(), v))
                        .orElse(""));
            }
            return out;
        }
    }

    private static String format(CustomFieldType type, CustomFieldValue v) {
        return switch (type) {
            case NUMBER -> v.getValueNumber() == null ? "" : v.getValueNumber().stripTrailingZeros().toPlainString();
            case DATE -> v.getValueDate() == null ? "" : v.getValueDate().toString();
            case BOOLEAN -> v.getValueBoolean() == null ? "" : v.getValueBoolean().toString();
            default -> v.getValueString() == null ? "" : v.getValueString();
        };
    }

    public static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    public static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    /**
     * Applies the optional, best-effort sort overrides from the request to the
     * already-built rows. A spec field matches a column when its final
     * dot-segment, normalized to lowercase alphanumerics, equals the normalized
     * header label (e.g. {@code "employee.lastName"} matches {@code "Last Name"}).
     * Comparison is lexicographic and stable; unknown fields are ignored, so the
     * builder's deterministic default order is preserved when no spec matches.
     */
    public static void applySort(List<String> headers, List<List<String>> rows,
                                 List<ReportParameters.SortSpec> specs) {
        if (specs.isEmpty() || rows.isEmpty()) {
            return;
        }
        List<String> normalizedHeaders = headers.stream().map(ReportSupport::normalize).toList();
        // Apply in reverse so the first spec is the primary key (stable sort).
        List<ReportParameters.SortSpec> ordered = new ArrayList<>(specs);
        java.util.Collections.reverse(ordered);
        for (ReportParameters.SortSpec spec : ordered) {
            if (spec.field() == null) {
                continue;
            }
            String key = normalize(lastSegment(spec.field()));
            int col = normalizedHeaders.indexOf(key);
            if (col < 0) {
                continue;
            }
            Comparator<List<String>> byCol = Comparator.comparing(
                    row -> col < row.size() ? row.get(col) : "",
                    Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));
            rows.sort(spec.descending() ? byCol.reversed() : byCol);
        }
    }

    private static String lastSegment(String field) {
        int dot = field.lastIndexOf('.');
        return dot >= 0 ? field.substring(dot + 1) : field;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9]", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** Validates and returns the (required) inclusive reporting window. */
    public DateWindow requireWindow(ReportParameters params) {
        if (params.from() == null || params.to() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "Report requires 'from' and 'to' dates");
        }
        if (params.from().isAfter(params.to())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                    "'from' must be on or before 'to'");
        }
        return new DateWindow(params.from(), params.to());
    }

    /** A closed [from, to] date range, with UTC-instant boundaries for time-based tables. */
    public record DateWindow(LocalDate from, LocalDate to) {
        public java.time.Instant fromInstantUtc() {
            return from.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        }

        public java.time.Instant toInstantExclusiveUtc() {
            return to.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        }
    }
}
