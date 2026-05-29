package com.attendance.report;

import com.attendance.exception.domain.ExceptionEvent;
import com.attendance.exception.domain.ExceptionSeverity;
import com.attendance.exception.domain.ExceptionStatus;
import com.attendance.exception.domain.ExceptionType;
import com.attendance.exception.repository.ExceptionEventRepository;
import com.attendance.identity.domain.User;
import com.attendance.identity.domain.UserStatus;
import com.attendance.identity.repository.UserRepository;
import com.attendance.leave.domain.LeaveRequest;
import com.attendance.leave.domain.LeaveRequestStatus;
import com.attendance.leave.domain.LeaveType;
import com.attendance.leave.repository.LeaveRequestRepository;
import com.attendance.leave.repository.LeaveTypeRepository;
import com.attendance.organization.domain.CustomFieldDefinition;
import com.attendance.organization.domain.CustomFieldEntityType;
import com.attendance.organization.domain.CustomFieldType;
import com.attendance.organization.domain.CustomFieldValue;
import com.attendance.organization.domain.Employee;
import com.attendance.organization.domain.EmployeeStatus;
import com.attendance.organization.domain.EmploymentType;
import com.attendance.organization.repository.CustomFieldDefinitionRepository;
import com.attendance.organization.repository.CustomFieldValueRepository;
import com.attendance.organization.repository.EmployeeRepository;
import com.attendance.report.domain.ReportJob;
import com.attendance.report.domain.ReportStatus;
import com.attendance.report.domain.ReportType;
import com.attendance.report.repository.ReportJobRepository;
import com.attendance.report.service.ReportGenerationService;
import com.attendance.report.service.ReportParameters;
import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.domain.PunchEvent;
import com.attendance.timecard.domain.PunchEventStatus;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.domain.TimeCardBreakdown;
import com.attendance.timecard.domain.TimeCardEdit;
import com.attendance.timecard.domain.TimeCardEditChangeType;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.repository.PunchEventRepository;
import com.attendance.timecard.repository.TimeCardEditRepository;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.domain.TimeCodeCategory;
import com.attendance.timecode.repository.TimeCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden-fixture test for the seven report types. A small, fully deterministic
 * dataset is built directly via repositories (fixed dates, names, punch times,
 * and edit timestamp), each report is generated, and the resulting CSV is
 * compared byte-for-byte against a committed fixture under
 * {@code src/test/resources/fixtures/reports/}.
 *
 * <p>To (re)generate the fixtures after an intentional format change, delete the
 * fixture file or run with {@code -Dreport.writeFixtures=true}; the test writes
 * the current output and skips the byte comparison for that run.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportGoldenTest {

    private static final Path FIXTURE_DIR = Path.of("src/test/resources/fixtures/reports");
    private static final LocalDate FROM = LocalDate.of(2026, 5, 1);
    private static final LocalDate TO = LocalDate.of(2026, 5, 31);

    @Autowired ReportGenerationService reportService;
    @Autowired ReportJobRepository reportJobRepository;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired TimeCodeRepository timeCodeRepository;
    @Autowired DailyTimeCardRepository dailyTimeCardRepository;
    @Autowired PunchEventRepository punchEventRepository;
    @Autowired ExceptionEventRepository exceptionEventRepository;
    @Autowired LeaveRequestRepository leaveRequestRepository;
    @Autowired LeaveTypeRepository leaveTypeRepository;
    @Autowired TimeCardEditRepository timeCardEditRepository;
    @Autowired UserRepository userRepository;
    @Autowired CustomFieldDefinitionRepository customFieldDefinitionRepository;
    @Autowired CustomFieldValueRepository customFieldValueRepository;

    private UUID aliceId;     // E001, Smith
    private UUID bobId;       // E002, Jones

    @BeforeEach
    void seed() {
        clear();

        TimeCode reg = timeCode("REG", "Regular", TimeCodeCategory.ATTENDANCE);
        TimeCode anl = timeCode("ANL", "Annual Leave", TimeCodeCategory.LEAVE);

        Employee alice = employee("E001", "Alice", "Smith");
        Employee bob = employee("E002", "Bob", "Jones");
        aliceId = alice.getId();
        bobId = bob.getId();

        // Custom field: cost_center on Alice only.
        CustomFieldDefinition cf = new CustomFieldDefinition();
        cf.setEntityType(CustomFieldEntityType.EMPLOYEE);
        cf.setFieldKey("cost_center");
        cf.setDisplayLabel("Cost Center");
        cf.setFieldType(CustomFieldType.STRING);
        cf.setRequired(false);
        cf.setDisplayOrder(0);
        customFieldDefinitionRepository.save(cf);
        CustomFieldValue cv = new CustomFieldValue();
        cv.setDefinitionId(cf.getId());
        cv.setEntityId(aliceId);
        cv.setValueString("CC-100");
        customFieldValueRepository.save(cv);

        // Punches: Alice worked 13:00–21:00 UTC on 2026-05-04.
        punch(aliceId, "EV-1", PunchEventType.CHECK_IN, Instant.parse("2026-05-04T13:00:00Z"));
        punch(aliceId, "EV-2", PunchEventType.CHECK_OUT, Instant.parse("2026-05-04T21:00:00Z"));

        // Time cards: Alice PRESENT (480m + REG breakdown), Bob ABSENT.
        DailyTimeCard aliceCard = new DailyTimeCard();
        aliceCard.setEmployeeId(aliceId);
        aliceCard.setWorkDate(LocalDate.of(2026, 5, 4));
        aliceCard.setComputedAt(Instant.parse("2026-05-04T21:05:00Z"));
        aliceCard.setScheduledStartUtc(Instant.parse("2026-05-04T13:00:00Z"));
        aliceCard.setScheduledEndUtc(Instant.parse("2026-05-04T21:00:00Z"));
        aliceCard.setActualStartUtc(Instant.parse("2026-05-04T13:00:00Z"));
        aliceCard.setActualEndUtc(Instant.parse("2026-05-04T21:00:00Z"));
        aliceCard.setWorkedMinutes(480);
        aliceCard.setBreakMinutes(0);
        aliceCard.setOvertimeMinutes(0);
        aliceCard.setLateMinutes(0);
        aliceCard.setEarlyOutMinutes(0);
        aliceCard.setStatus(DailyTimeCardStatus.PRESENT);
        TimeCardBreakdown bd = new TimeCardBreakdown();
        bd.setDailyTimeCard(aliceCard);
        bd.setTimeCodeId(reg.getId());
        bd.setMinutes(480);
        bd.setRatedMinutes(480);
        bd.setSequenceOrder(0);
        aliceCard.getBreakdowns().add(bd);
        dailyTimeCardRepository.save(aliceCard);

        DailyTimeCard bobCard = new DailyTimeCard();
        bobCard.setEmployeeId(bobId);
        bobCard.setWorkDate(LocalDate.of(2026, 5, 4));
        bobCard.setComputedAt(Instant.parse("2026-05-04T21:05:00Z"));
        bobCard.setWorkedMinutes(0);
        bobCard.setBreakMinutes(0);
        bobCard.setOvertimeMinutes(0);
        bobCard.setLateMinutes(0);
        bobCard.setEarlyOutMinutes(0);
        bobCard.setStatus(DailyTimeCardStatus.ABSENT);
        dailyTimeCardRepository.save(bobCard);

        // Exception: Alice ABSENT_NO_LEAVE on 2026-05-05 (OPEN).
        ExceptionEvent ex = new ExceptionEvent();
        ex.setEmployeeId(aliceId);
        ex.setWorkDate(LocalDate.of(2026, 5, 5));
        ex.setExceptionType(ExceptionType.ABSENT_NO_LEAVE);
        ex.setSeverity(ExceptionSeverity.WARN);
        ex.setStatus(ExceptionStatus.OPEN);
        exceptionEventRepository.save(ex);

        // Leave: Bob, Annual Leave, full day 2026-05-06, approved.
        LeaveType lt = new LeaveType();
        lt.setTimeCodeId(anl.getId());
        lt.setName("Annual Leave");
        lt.setDefaultAnnualDays(new BigDecimal("20.00"));
        lt.setRequiresApproval(true);
        lt.setActive(true);
        leaveTypeRepository.save(lt);
        LeaveRequest lr = new LeaveRequest();
        lr.setEmployeeId(bobId);
        lr.setLeaveTypeId(lt.getId());
        lr.setStartDate(LocalDate.of(2026, 5, 6));
        lr.setEndDate(LocalDate.of(2026, 5, 6));
        lr.setHalfDay(false);
        lr.setReason("vacation");
        lr.setStatus(LeaveRequestStatus.APPROVED);
        lr.setRetroactive(false);
        leaveRequestRepository.save(lr);

        // Edit: a manual punch edit on Alice's card by a known auditor. Reuse the
        // user across test methods (the shared context keeps the row around).
        User auditor = userRepository.findByUsernameIgnoreCase("report-auditor")
                .orElseGet(() -> {
                    User u = new User();
                    u.setUsername("report-auditor");
                    u.setEmail("report-auditor@local");
                    u.setPasswordHash("x");
                    u.setStatus(UserStatus.ACTIVE);
                    return userRepository.save(u);
                });
        TimeCardEdit edit = new TimeCardEdit();
        edit.setDailyTimeCardId(aliceCard.getId());
        edit.setChangeType(TimeCardEditChangeType.PUNCH_EDIT);
        edit.setReason("fix clock drift");
        edit.setBeforeJson("{\"time\":\"13:05\"}");
        edit.setAfterJson("{\"time\":\"13:00\"}");
        edit.setEditedByUserId(auditor.getId());
        edit.setEditedAt(Instant.parse("2026-05-20T10:00:00Z"));
        timeCardEditRepository.save(edit);
    }

    @Test
    void daily_matchesGolden() {
        assertGolden("daily", ReportType.DAILY,
                new ReportParameters(FROM, TO, null, null, null, null, null, null));
    }

    @Test
    void dailySummary_withCustomField_matchesGolden() {
        assertGolden("daily-summary", ReportType.DAILY_SUMMARY,
                new ReportParameters(FROM, TO, null, null, null, null, List.of("cost_center"), null));
    }

    @Test
    void individual_matchesGolden() {
        assertGolden("individual", ReportType.INDIVIDUAL,
                new ReportParameters(FROM, TO, aliceId, null, null, null, null, null));
    }

    @Test
    void individualSummary_matchesGolden() {
        assertGolden("individual-summary", ReportType.INDIVIDUAL_SUMMARY,
                new ReportParameters(FROM, TO, null, null, null, null, null, null));
    }

    @Test
    void leave_matchesGolden() {
        assertGolden("leave", ReportType.LEAVE,
                new ReportParameters(FROM, TO, null, null, null, null, null, null));
    }

    @Test
    void exception_matchesGolden() {
        assertGolden("exception", ReportType.EXCEPTION,
                new ReportParameters(FROM, TO, null, null, null, null, null, null));
    }

    @Test
    void modifiedPunchLog_matchesGolden() {
        assertGolden("modified-punch-log", ReportType.MODIFIED_PUNCH_LOG,
                new ReportParameters(FROM, TO, null, null, null, null, null, null));
    }

    private void assertGolden(String name, ReportType type, ReportParameters params) {
        ReportJob job = reportService.queue(type, params, null);
        reportService.runReport(job.getId());
        ReportJob done = reportJobRepository.findById(job.getId()).orElseThrow();
        assertThat(done.getStatus())
                .as("report %s should complete; error=%s", name, done.getErrorMessage())
                .isEqualTo(ReportStatus.DONE);

        String actual = readFile(Path.of(done.getFilePath()));
        // Sanity: non-empty with a header line.
        assertThat(actual).contains("\r\n");

        Path fixture = FIXTURE_DIR.resolve(name + ".csv");
        boolean writeMode = Boolean.getBoolean("report.writeFixtures");
        if (writeMode || !Files.exists(fixture)) {
            writeFile(fixture, actual);
            return;
        }
        assertThat(actual)
                .as("report %s should match its golden fixture (byte-for-byte)", name)
                .isEqualTo(readFile(fixture));
    }

    // ---- seed helpers ----

    private TimeCode timeCode(String code, String fullName, TimeCodeCategory category) {
        TimeCode tc = new TimeCode();
        tc.setCode(code);
        tc.setName(fullName);
        tc.setCategory(category);
        tc.setRate(new BigDecimal("1.00"));
        tc.setColor("#3b82f6");
        tc.setPaid(true);
        tc.setCountsForAttendance(category == TimeCodeCategory.ATTENDANCE);
        tc.setActive(true);
        return timeCodeRepository.save(tc);
    }

    private Employee employee(String code, String first, String last) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFirstName(first);
        e.setLastName(last);
        e.setEmploymentType(EmploymentType.FULL_TIME);
        e.setHireDate(LocalDate.of(2024, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        return employeeRepository.save(e);
    }

    private void punch(UUID employeeId, String externalId, PunchEventType type, Instant when) {
        PunchEvent p = new PunchEvent();
        p.setEmployeeId(employeeId);
        p.setIngestionSourceId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
        p.setExternalEventId(externalId);
        p.setEventType(type);
        p.setEventTimeUtc(when);
        p.setStatus(PunchEventStatus.PROCESSED);
        p.setProcessedAt(when);
        punchEventRepository.save(p);
    }

    private void clear() {
        reportJobRepository.deleteAll();
        timeCardEditRepository.deleteAll();
        exceptionEventRepository.deleteAll();
        leaveRequestRepository.deleteAll();
        leaveTypeRepository.deleteAll();
        dailyTimeCardRepository.deleteAll();
        punchEventRepository.deleteAll();
        customFieldValueRepository.deleteAll();
        customFieldDefinitionRepository.deleteAll();
        employeeRepository.deleteAll();
        timeCodeRepository.deleteAll();
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Could not read " + path, ex);
        }
    }

    private static void writeFile(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Could not write " + path, ex);
        }
    }
}
