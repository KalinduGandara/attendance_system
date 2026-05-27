package com.attendance.organization.service;

import com.attendance.common.csv.CsvReader;
import com.attendance.common.error.ApiException;
import com.attendance.organization.domain.Department;
import com.attendance.organization.domain.Employee;
import com.attendance.organization.domain.EmployeeImportJob;
import com.attendance.organization.domain.EmployeeImportStatus;
import com.attendance.organization.domain.EmployeeStatus;
import com.attendance.organization.domain.EmploymentType;
import com.attendance.organization.repository.DepartmentRepository;
import com.attendance.organization.repository.EmployeeImportJobRepository;
import com.attendance.organization.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk CSV import of employees.
 *
 * <p>Synchronous when row count is below {@link #SYNC_THRESHOLD}; async otherwise.
 *
 * <p>CSV columns (header row required, case-insensitive, all but the marked-optional ones required):
 * {@code employeeCode, firstName, lastName, email?, phone?, departmentName?, managerCode?,
 * employmentType?, hireDate, terminationDate?, timezone?, status?}.
 */
@Service
public class EmployeeImportService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeImportService.class);
    public static final int SYNC_THRESHOLD = 1000;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeImportJobRepository jobRepository;
    private final ObjectProvider<EmployeeImportService> self;

    public EmployeeImportService(EmployeeRepository employeeRepository,
                                 DepartmentRepository departmentRepository,
                                 EmployeeImportJobRepository jobRepository,
                                 ObjectProvider<EmployeeImportService> self) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.jobRepository = jobRepository;
        this.self = self;
    }

    public EmployeeImportJob submit(MultipartFile file, UUID requestedBy) {
        List<List<String>> rows = parseRows(file);
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation", "CSV is empty");
        }
        Map<String, Integer> headerIndex = parseHeader(rows.get(0));
        List<List<String>> data = rows.subList(1, rows.size());

        EmployeeImportJob job = new EmployeeImportJob();
        job.setFileName(file.getOriginalFilename());
        job.setTotalRows(data.size());
        job.setRequestedBy(requestedBy);
        job.setStatus(EmployeeImportStatus.QUEUED);
        jobRepository.save(job);

        if (data.size() < SYNC_THRESHOLD) {
            self.getObject().runImport(job.getId(), headerIndex, data);
            return jobRepository.findById(job.getId()).orElse(job);
        }
        self.getObject().runImportAsync(job.getId(), headerIndex, data);
        return job;
    }

    @Async("importExecutor")
    public void runImportAsync(UUID jobId, Map<String, Integer> header, List<List<String>> rows) {
        runImport(jobId, header, rows);
    }

    @Transactional
    public void runImport(UUID jobId, Map<String, Integer> header, List<List<String>> rows) {
        EmployeeImportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Import job missing: " + jobId));
        job.setStatus(EmployeeImportStatus.RUNNING);
        job.setStartedAt(Instant.now());

        Map<String, UUID> deptCache = new HashMap<>();
        Map<String, UUID> managerCache = new HashMap<>();
        StringBuilder errors = new StringBuilder();
        int created = 0, updated = 0, processed = 0, errCount = 0;

        try {
            for (int i = 0; i < rows.size(); i++) {
                List<String> row = rows.get(i);
                int lineNo = i + 2;
                processed++;
                try {
                    String code = required(row, header, "employeeCode", lineNo);
                    String firstName = required(row, header, "firstName", lineNo);
                    String lastName = required(row, header, "lastName", lineNo);
                    LocalDate hireDate = LocalDate.parse(required(row, header, "hireDate", lineNo));
                    Employee e = employeeRepository.findByEmployeeCode(code).orElse(null);
                    boolean isNew = e == null;
                    if (isNew) {
                        e = new Employee();
                        e.setEmployeeCode(code);
                    }
                    e.setFirstName(firstName);
                    e.setLastName(lastName);
                    e.setEmail(optional(row, header, "email"));
                    e.setPhone(optional(row, header, "phone"));
                    String deptName = optional(row, header, "departmentName");
                    if (deptName != null) {
                        UUID deptId = resolveDept(deptName, deptCache);
                        if (deptId == null) {
                            throw new IllegalArgumentException("Unknown department: " + deptName);
                        }
                        e.setDepartmentId(deptId);
                    }
                    String managerCode = optional(row, header, "managerCode");
                    if (managerCode != null) {
                        UUID mgrId = resolveManager(managerCode, managerCache);
                        if (mgrId == null) {
                            throw new IllegalArgumentException("Unknown manager code: " + managerCode);
                        }
                        e.setManagerId(mgrId);
                    }
                    String et = optional(row, header, "employmentType");
                    e.setEmploymentType(et == null ? EmploymentType.FULL_TIME
                            : EmploymentType.valueOf(et.toUpperCase(Locale.ROOT)));
                    e.setHireDate(hireDate);
                    String term = optional(row, header, "terminationDate");
                    if (term != null) {
                        e.setTerminationDate(LocalDate.parse(term));
                    }
                    e.setTimezone(optional(row, header, "timezone"));
                    String st = optional(row, header, "status");
                    e.setStatus(st == null ? EmployeeStatus.ACTIVE
                            : EmployeeStatus.valueOf(st.toUpperCase(Locale.ROOT)));
                    employeeRepository.save(e);
                    if (isNew) {
                        created++;
                        managerCache.put(code, e.getId());
                    } else {
                        updated++;
                    }
                } catch (RuntimeException ex) {
                    errCount++;
                    errors.append("Row ").append(lineNo).append(": ").append(ex.getMessage()).append('\n');
                    if (errors.length() > 60_000) {
                        errors.append("…(truncated)\n");
                        break;
                    }
                }
                job.setProcessedRows(processed);
            }
            job.setCreatedCount(created);
            job.setUpdatedCount(updated);
            job.setErrorCount(errCount);
            job.setErrorReport(errors.length() == 0 ? null : errors.toString());
            job.setStatus(errCount > 0 && created + updated == 0
                    ? EmployeeImportStatus.FAILED
                    : EmployeeImportStatus.DONE);
        } catch (RuntimeException ex) {
            log.error("Employee import failed", ex);
            job.setStatus(EmployeeImportStatus.FAILED);
            job.setErrorMessage(ex.getMessage());
        } finally {
            job.setCompletedAt(Instant.now());
        }
    }

    @Transactional(readOnly = true)
    public EmployeeImportJob get(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not-found",
                        "Import job not found"));
    }

    private UUID resolveDept(String name, Map<String, UUID> cache) {
        return cache.computeIfAbsent(name.toLowerCase(Locale.ROOT), k ->
                departmentRepository.findAll().stream()
                        .filter(d -> d.getName().equalsIgnoreCase(name))
                        .findFirst().map(Department::getId).orElse(null));
    }

    private UUID resolveManager(String code, Map<String, UUID> cache) {
        return cache.computeIfAbsent(code, k ->
                employeeRepository.findByEmployeeCode(code).map(Employee::getId).orElse(null));
    }

    private String required(List<String> row, Map<String, Integer> header, String key, int line) {
        String v = optional(row, header, key);
        if (v == null) {
            throw new IllegalArgumentException("Missing required column '" + key + "'");
        }
        return v;
    }

    private String optional(List<String> row, Map<String, Integer> header, String key) {
        Integer idx = header.get(key.toLowerCase(Locale.ROOT));
        if (idx == null || idx >= row.size()) {
            return null;
        }
        String v = row.get(idx);
        return (v == null || v.isBlank()) ? null : v.trim();
    }

    private List<List<String>> parseRows(MultipartFile file) {
        try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return CsvReader.readAll(reader);
        } catch (IOException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation", "Could not read CSV: " + ex.getMessage());
        }
    }

    private Map<String, Integer> parseHeader(List<String> header) {
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).trim();
            if (!key.isEmpty()) {
                idx.put(key.toLowerCase(Locale.ROOT), i);
            }
        }
        for (String required : new String[]{"employeecode", "firstname", "lastname", "hiredate"}) {
            if (!idx.containsKey(required)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "validation",
                        "CSV header missing required column: " + required);
            }
        }
        return idx;
    }
}
