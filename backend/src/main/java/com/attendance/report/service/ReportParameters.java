package com.attendance.report.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Parsed {@code parameters} object of a report request. All fields are optional
 * at the JSON level; each report validates what it actually needs.
 *
 * @param from                inclusive start date of the reporting window
 * @param to                  inclusive end date of the reporting window
 * @param employeeId          restrict to a single employee
 * @param departmentId        restrict to one department
 * @param groupId             restrict to one group
 * @param status              status filter (LEAVE / EXCEPTION reports)
 * @param includeCustomFields employee custom-field keys to append as columns
 * @param sort                optional ordering overrides applied to the rows
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReportParameters(
        LocalDate from,
        LocalDate to,
        UUID employeeId,
        UUID departmentId,
        UUID groupId,
        String status,
        List<String> includeCustomFields,
        List<SortSpec> sort) {

    public List<String> includeCustomFields() {
        return includeCustomFields == null ? List.of() : includeCustomFields;
    }

    public List<SortSpec> sort() {
        return sort == null ? List.of() : sort;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SortSpec(String field, String dir) {
        public boolean descending() {
            return dir != null && dir.equalsIgnoreCase("desc");
        }
    }
}
