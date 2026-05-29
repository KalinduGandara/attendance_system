package com.attendance.leave;

import com.attendance.exception.domain.ExceptionStatus;
import com.attendance.exception.domain.ExceptionType;
import com.attendance.exception.repository.ExceptionEventRepository;
import com.attendance.identity.domain.User;
import com.attendance.identity.domain.UserStatus;
import com.attendance.identity.repository.UserRepository;
import com.attendance.leave.domain.HalfDayPart;
import com.attendance.leave.domain.LeaveRequestStatus;
import com.attendance.leave.domain.LeaveType;
import com.attendance.leave.repository.LeaveBalanceRepository;
import com.attendance.leave.repository.LeaveRequestRepository;
import com.attendance.leave.repository.LeaveTypeRepository;
import com.attendance.leave.service.LeaveBalanceService;
import com.attendance.leave.service.LeaveRequestService;
import com.attendance.leave.service.LeaveTypeService;
import com.attendance.leave.web.LeaveDtos;
import com.attendance.platform.security.AppPrincipal;
import com.attendance.timecard.domain.DailyTimeCard;
import com.attendance.timecard.domain.DailyTimeCardStatus;
import com.attendance.timecard.integration.Phase5SeedData;
import com.attendance.timecard.repository.DailyTimeCardRepository;
import com.attendance.timecard.service.TimeCardReadService;
import com.attendance.timecard.service.TimeCardRecomputeService;
import com.attendance.timecard.web.TimeCardDtos;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.domain.TimeCodeCategory;
import com.attendance.timecode.repository.TimeCodeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LeaveRequestLifecycleTest {

    @Autowired Phase5SeedData seed;
    @Autowired LeaveTypeService leaveTypeService;
    @Autowired LeaveRequestService leaveRequestService;
    @Autowired LeaveBalanceService leaveBalanceService;
    @Autowired LeaveTypeRepository leaveTypeRepository;
    @Autowired LeaveBalanceRepository leaveBalanceRepository;
    @Autowired LeaveRequestRepository leaveRequestRepository;
    @Autowired TimeCodeRepository timeCodeRepository;
    @Autowired TimeCardRecomputeService recomputeService;
    @Autowired DailyTimeCardRepository dailyTimeCardRepository;
    @Autowired TimeCardReadService timeCardReadService;
    @Autowired ExceptionEventRepository exceptionEventRepository;
    @Autowired UserRepository userRepository;

    private Phase5SeedData.Seeded data;
    private UUID leaveTypeId;

    @BeforeEach
    void setUp() {
        data = seed.seedAll();
        TimeCode pto = new TimeCode();
        pto.setCode("PTO");
        pto.setName("Paid time off");
        pto.setCategory(TimeCodeCategory.LEAVE);
        pto.setRate(new BigDecimal("1.00"));
        pto.setColor("#10b981");
        pto.setPaid(true);
        pto.setCountsForAttendance(false);
        pto.setActive(true);
        UUID ptoId = timeCodeRepository.saveAndFlush(pto).getId();

        leaveTypeId = leaveTypeService.create(new LeaveDtos.LeaveTypeRequest(
                "Annual leave", ptoId, new BigDecimal("20"), true, null, true)).id();

        // Seed the balance up-front so the deductions are observable.
        leaveBalanceService.adjust(data.employeeId(), new LeaveDtos.BalanceAdjustment(
                leaveTypeId, 2026, new BigDecimal("20")));

        // Switch to a known acting user so audit + lifecycle fields populate.
        User u = userRepository.findAll().stream().findFirst().orElseGet(() -> {
            User n = new User();
            n.setUsername("test-approver-" + UUID.randomUUID());
            n.setEmail(n.getUsername() + "@local");
            n.setPasswordHash("x");
            n.setStatus(UserStatus.ACTIVE);
            return userRepository.saveAndFlush(n);
        });
        AppPrincipal principal = new AppPrincipal(u.getId(), u.getUsername(),
                List.of("leave.read", "leave.approve"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void approvingLeaveOnAbsentDay_flipsTimeCardToLeaveAndClosesExceptions() {
        LocalDate workDate = LocalDate.of(2026, 5, 28);  // Thursday — scheduled, no punches → ABSENT
        recomputeService.recompute(data.employeeId(), workDate);

        DailyTimeCard before = dailyTimeCardRepository
                .findByEmployeeIdAndWorkDate(data.employeeId(), workDate).orElseThrow();
        assertThat(before.getStatus()).isEqualTo(DailyTimeCardStatus.ABSENT);
        assertThat(exceptionEventRepository.findByEmployeeIdAndWorkDate(data.employeeId(), workDate))
                .anyMatch(e -> e.getExceptionType() == ExceptionType.ABSENT_NO_LEAVE
                        && e.getStatus() == ExceptionStatus.OPEN);

        LeaveDtos.LeaveRequestResponse req = leaveRequestService.create(new LeaveDtos.LeaveRequestRequest(
                data.employeeId(), leaveTypeId, workDate, workDate, false, null,
                "Family emergency", true));
        assertThat(req.status()).isEqualTo(LeaveRequestStatus.PENDING);

        leaveRequestService.approve(req.id(), null);

        DailyTimeCard after = dailyTimeCardRepository
                .findByEmployeeIdAndWorkDate(data.employeeId(), workDate).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(DailyTimeCardStatus.LEAVE);

        // ABSENT_NO_LEAVE no longer applicable → recompute drops it from OPEN.
        assertThat(exceptionEventRepository.findByEmployeeIdAndWorkDate(data.employeeId(), workDate))
                .noneMatch(e -> e.getExceptionType() == ExceptionType.ABSENT_NO_LEAVE
                        && e.getStatus() == ExceptionStatus.OPEN);

        BigDecimal balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(data.employeeId(), leaveTypeId, 2026)
                .orElseThrow().getBalanceDays();
        assertThat(balance).isEqualByComparingTo("19");
    }

    @Test
    void halfDayLeave_halvesBalanceAndPartiallyFillsTimeCard() {
        LocalDate day = LocalDate.of(2026, 5, 28);
        LeaveDtos.LeaveRequestResponse req = leaveRequestService.create(new LeaveDtos.LeaveRequestRequest(
                data.employeeId(), leaveTypeId, day, day, true, HalfDayPart.FIRST_HALF,
                "Doctor visit", false));
        leaveRequestService.approve(req.id(), null);

        BigDecimal balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(data.employeeId(), leaveTypeId, 2026)
                .orElseThrow().getBalanceDays();
        assertThat(balance).isEqualByComparingTo("19.5");

        DailyTimeCard card = dailyTimeCardRepository
                .findByEmployeeIdAndWorkDate(data.employeeId(), day).orElseThrow();
        TimeCardDtos.TimeCardResponse detail = timeCardReadService.get(card.getId());
        // Half of an 8h shift = 240m attributed to the leave time code.
        assertThat(detail.breakdown()).anyMatch(b -> b.minutes() == 240);
    }

    @Test
    void cancelApprovedLeave_refundsBalance() {
        LocalDate day = LocalDate.of(2026, 5, 28);
        LeaveDtos.LeaveRequestResponse req = leaveRequestService.create(new LeaveDtos.LeaveRequestRequest(
                data.employeeId(), leaveTypeId, day, day, false, null, "x", false));
        leaveRequestService.approve(req.id(), null);
        leaveRequestService.cancel(req.id());

        BigDecimal balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(data.employeeId(), leaveTypeId, 2026)
                .orElseThrow().getBalanceDays();
        assertThat(balance).isEqualByComparingTo("20");
    }

    @Test
    void rejectingRequest_keepsBalanceUntouched() {
        LocalDate day = LocalDate.of(2026, 5, 28);
        LeaveDtos.LeaveRequestResponse req = leaveRequestService.create(new LeaveDtos.LeaveRequestRequest(
                data.employeeId(), leaveTypeId, day, day, false, null, "x", false));
        leaveRequestService.reject(req.id(), new LeaveDtos.LeaveDecision("Not enough notice"));

        BigDecimal balance = leaveBalanceRepository
                .findByEmployeeIdAndLeaveTypeIdAndYear(data.employeeId(), leaveTypeId, 2026)
                .orElseThrow().getBalanceDays();
        assertThat(balance).isEqualByComparingTo("20");
    }

    @Test
    void leaveType_mustReferenceLeaveCategoryTimeCode() {
        // The REG time code is ATTENDANCE — reject it.
        assertThatThrownBy(() -> leaveTypeService.create(new LeaveDtos.LeaveTypeRequest(
                "Bogus", data.regTimeCodeId(), new BigDecimal("0"), false, null, true)))
                .hasMessageContaining("LEAVE-category");
    }

    @Test
    void halfDayRequest_mustBeSingleDay() {
        LeaveType t = leaveTypeRepository.findById(leaveTypeId).orElseThrow();
        assertThat(t.isActive()).isTrue();

        assertThatThrownBy(() -> leaveRequestService.create(new LeaveDtos.LeaveRequestRequest(
                data.employeeId(), leaveTypeId,
                LocalDate.of(2026, 5, 28), LocalDate.of(2026, 5, 29),
                true, HalfDayPart.FIRST_HALF, "x", false)))
                .hasMessageContaining("single day");
    }
}
