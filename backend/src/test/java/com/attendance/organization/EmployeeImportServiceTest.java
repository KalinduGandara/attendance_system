package com.attendance.organization;

import com.attendance.organization.domain.EmployeeImportJob;
import com.attendance.organization.domain.EmployeeImportStatus;
import com.attendance.organization.repository.EmployeeImportJobRepository;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.organization.service.EmployeeImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class EmployeeImportServiceTest {

    @Autowired EmployeeImportService importService;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired EmployeeImportJobRepository jobRepository;

    @BeforeEach
    void clean() {
        employeeRepository.deleteAll();
        jobRepository.deleteAll();
    }

    @Test
    void sync_import_creates_employees() {
        String csv = """
                employeeCode,firstName,lastName,email,employmentType,hireDate
                E100,Alice,Doe,alice@example.com,FULL_TIME,2024-01-15
                E101,Bob,Smith,bob@example.com,CONTRACT,2024-02-20
                """;
        MockMultipartFile file = new MockMultipartFile("file", "emp.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        EmployeeImportJob job = importService.submit(file, null);
        assertThat(job.getStatus()).isEqualTo(EmployeeImportStatus.DONE);
        assertThat(job.getCreatedCount()).isEqualTo(2);
        assertThat(job.getErrorCount()).isZero();
        assertThat(employeeRepository.count()).isEqualTo(2);
    }

    @Test
    void second_import_updates_existing_rows() {
        String first = """
                employeeCode,firstName,lastName,hireDate
                E200,Alice,Doe,2024-01-01
                """;
        importService.submit(new MockMultipartFile("file", "1.csv", "text/csv",
                first.getBytes(StandardCharsets.UTF_8)), null);

        String second = """
                employeeCode,firstName,lastName,hireDate
                E200,Alicia,Doe,2024-01-01
                """;
        EmployeeImportJob job = importService.submit(new MockMultipartFile("file", "2.csv", "text/csv",
                second.getBytes(StandardCharsets.UTF_8)), null);

        assertThat(job.getStatus()).isEqualTo(EmployeeImportStatus.DONE);
        assertThat(job.getUpdatedCount()).isEqualTo(1);
        assertThat(employeeRepository.findByEmployeeCode("E200")).get()
                .extracting(e -> e.getFirstName()).isEqualTo("Alicia");
    }

    @Test
    void missing_required_column_rejects_upload() {
        String csv = "firstName,lastName,hireDate\nAlice,Doe,2024-01-01\n";
        MockMultipartFile file = new MockMultipartFile("file", "emp.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        try {
            importService.submit(file, null);
            assertThat(false).as("expected exception").isTrue();
        } catch (Exception ex) {
            assertThat(ex.getMessage()).contains("employeecode");
        }
    }

    @Test
    void row_errors_are_collected_but_other_rows_still_persist() {
        String csv = """
                employeeCode,firstName,lastName,hireDate
                E300,Alice,Doe,2024-01-01
                E301,Bob,Smith,not-a-date
                E302,Carol,Lee,2024-03-01
                """;
        MockMultipartFile file = new MockMultipartFile("file", "emp.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        EmployeeImportJob job = importService.submit(file, null);

        assertThat(job.getCreatedCount()).isEqualTo(2);
        assertThat(job.getErrorCount()).isEqualTo(1);
        assertThat(job.getErrorReport()).contains("Row 3");
    }
}
