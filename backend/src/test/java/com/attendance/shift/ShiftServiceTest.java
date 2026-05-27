package com.attendance.shift;

import com.attendance.common.error.ApiException;
import com.attendance.shift.domain.BreakKind;
import com.attendance.shift.domain.GraceKind;
import com.attendance.shift.domain.RoundingKind;
import com.attendance.shift.domain.RoundingMode;
import com.attendance.shift.domain.ShiftType;
import com.attendance.shift.repository.ShiftRepository;
import com.attendance.shift.service.ShiftService;
import com.attendance.shift.web.ShiftDtos;
import com.attendance.timecode.domain.TimeCode;
import com.attendance.timecode.domain.TimeCodeCategory;
import com.attendance.timecode.repository.TimeCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShiftServiceTest {

    @Autowired ShiftService shiftService;
    @Autowired ShiftRepository shiftRepository;
    @Autowired TimeCodeRepository timeCodeRepository;

    private UUID regId;
    private UUID otAId;
    private UUID otBId;
    private UUID leaveId;

    @BeforeEach
    void seed() {
        shiftRepository.deleteAll();
        timeCodeRepository.deleteAll();
        regId = saveTimeCode("REG", TimeCodeCategory.ATTENDANCE, "1.00");
        otAId = saveTimeCode("OT-A", TimeCodeCategory.OVERTIME, "1.50");
        otBId = saveTimeCode("OT-B", TimeCodeCategory.OVERTIME, "2.00");
        leaveId = saveTimeCode("ANN", TimeCodeCategory.LEAVE, "1.00");
    }

    private UUID saveTimeCode(String code, TimeCodeCategory cat, String rate) {
        TimeCode tc = new TimeCode();
        tc.setCode(code);
        tc.setName(code);
        tc.setCategory(cat);
        tc.setRate(new BigDecimal(rate));
        tc.setColor("#000000");
        tc.setPaid(true);
        tc.setCountsForAttendance(cat != TimeCodeCategory.LEAVE);
        tc.setActive(true);
        return timeCodeRepository.saveAndFlush(tc).getId();
    }

    private ShiftDtos.ShiftRequest fixedShiftWithTwoOtTiers() {
        return new ShiftDtos.ShiftRequest(
                "Day 9-5", ShiftType.FIXED, "#3b82f6",
                null, "Standard 9 to 5", true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 1020, null)), // 9:00 - 17:00
                List.of(new ShiftDtos.RoundingRuleRequest(RoundingKind.PUNCH_IN, 15, RoundingMode.NEAREST)),
                List.of(new ShiftDtos.GraceRuleRequest(GraceKind.LATE_IN, 5)),
                List.of(new ShiftDtos.BreakRuleRequest(
                        "Lunch", BreakKind.AUTO_DEDUCT, 30, 720, null, false, null)),
                List.of(
                        new ShiftDtos.OvertimeRuleRequest(1, 480, otAId, 120),
                        new ShiftDtos.OvertimeRuleRequest(2, 600, otBId, null)),
                null, null);
    }

    @Test
    void create_fixed_shift_with_two_ot_tiers_round_trip() {
        var created = shiftService.create(fixedShiftWithTwoOtTiers());
        assertThat(created.shiftType()).isEqualTo(ShiftType.FIXED);
        assertThat(created.segments()).hasSize(1);
        assertThat(created.breakRules()).singleElement()
                .satisfies(b -> {
                    assertThat(b.kind()).isEqualTo(BreakKind.AUTO_DEDUCT);
                    assertThat(b.durationMinutes()).isEqualTo(30);
                });
        assertThat(created.overtimeRules()).hasSize(2);
        assertThat(created.overtimeRules().get(0).afterMinutesWorked()).isEqualTo(480);
        assertThat(created.overtimeRules().get(1).afterMinutesWorked()).isEqualTo(600);
    }

    @Test
    void fixed_shift_without_segment_rejected() {
        var req = new ShiftDtos.ShiftRequest(
                "Bad", ShiftType.FIXED, "#3b82f6",
                null, null, true, regId,
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null);
        assertThatThrownBy(() -> shiftService.create(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("FIXED shift requires at least one segment");
    }

    @Test
    void flexible_shift_requires_required_minutes_within_window() {
        var oversized = new ShiftDtos.ShiftRequest(
                "Flex", ShiftType.FLEXIBLE, "#3b82f6",
                null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 660, 240)), // 2h window but 4h req
                List.of(), List.of(), List.of(), List.of(), null, null);
        assertThatThrownBy(() -> shiftService.create(oversized))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("requiredMinutes exceeds its window");

        var noReq = new ShiftDtos.ShiftRequest(
                "Flex2", ShiftType.FLEXIBLE, "#3b82f6",
                null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 1020, null)),
                List.of(), List.of(), List.of(), List.of(), null, null);
        assertThatThrownBy(() -> shiftService.create(noReq))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("requiredMinutes");
    }

    @Test
    void ot_tiers_must_be_strictly_increasing() {
        var req = new ShiftDtos.ShiftRequest(
                "Bad OT", ShiftType.FIXED, "#3b82f6",
                null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 1020, null)),
                List.of(), List.of(), List.of(),
                List.of(
                        new ShiftDtos.OvertimeRuleRequest(1, 600, otAId, null),
                        new ShiftDtos.OvertimeRuleRequest(2, 600, otBId, null)),
                null, null);
        assertThatThrownBy(() -> shiftService.create(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("strictly increasing");
    }

    @Test
    void ot_tier_time_code_must_be_overtime_category() {
        var req = new ShiftDtos.ShiftRequest(
                "Bad TC", ShiftType.FIXED, "#3b82f6",
                null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 1020, null)),
                List.of(), List.of(), List.of(),
                List.of(new ShiftDtos.OvertimeRuleRequest(1, 480, regId, null)),
                null, null);
        assertThatThrownBy(() -> shiftService.create(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("category OVERTIME");
    }

    @Test
    void attendance_time_code_cannot_be_leave_category() {
        var req = new ShiftDtos.ShiftRequest(
                "Bad", ShiftType.FIXED, "#3b82f6",
                null, null, true, leaveId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 1020, null)),
                List.of(), List.of(), List.of(), List.of(), null, null);
        assertThatThrownBy(() -> shiftService.create(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ATTENDANCE or OVERTIME");
    }

    @Test
    void floating_shift_candidates_must_be_fixed_or_flexible() {
        // Build a FIXED and FLEXIBLE candidate first.
        var fixed = shiftService.create(fixedShiftWithTwoOtTiers());
        var flex = shiftService.create(new ShiftDtos.ShiftRequest(
                "Flex", ShiftType.FLEXIBLE, "#3b82f6",
                null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 480, 1080, 360)),
                List.of(), List.of(), List.of(), List.of(), null, null));

        // FLOATING referencing a FIXED + FLEXIBLE is valid.
        var floating = shiftService.create(new ShiftDtos.ShiftRequest(
                "Floating", ShiftType.FLOATING, "#3b82f6",
                null, null, true, regId,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                Set.of(fixed.id(), flex.id()), null));
        assertThat(floating.candidateShiftIds()).containsExactlyInAnyOrder(fixed.id(), flex.id());

        // Referencing another FLOATING is rejected.
        var bad = new ShiftDtos.ShiftRequest(
                "Floating2", ShiftType.FLOATING, "#3b82f6",
                null, null, true, regId,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                Set.of(floating.id()), null);
        assertThatThrownBy(() -> shiftService.create(bad))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("FIXED or FLEXIBLE");
    }

    @Test
    void floating_shift_requires_candidates() {
        var req = new ShiftDtos.ShiftRequest(
                "Empty Floating", ShiftType.FLOATING, "#3b82f6",
                null, null, true, regId,
                List.of(), List.of(), List.of(), List.of(), List.of(),
                Set.of(), null);
        assertThatThrownBy(() -> shiftService.create(req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("at least one candidate");
    }

    @Test
    void update_replaces_children_atomically_and_bumps_version() {
        var created = shiftService.create(fixedShiftWithTwoOtTiers());

        var updated = shiftService.update(created.id(), new ShiftDtos.ShiftRequest(
                "Day 9-5 updated", ShiftType.FIXED, "#3b82f6",
                null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 1020, null)),
                List.of(),
                List.of(new ShiftDtos.GraceRuleRequest(GraceKind.LATE_IN, 10)), // changed
                List.of(), // breakRules cleared
                List.of(new ShiftDtos.OvertimeRuleRequest(1, 480, otAId, null)), // only one OT
                null, null));

        assertThat(updated.name()).isEqualTo("Day 9-5 updated");
        assertThat(updated.graceRules()).hasSize(1);
        assertThat(updated.graceRules().get(0).minutes()).isEqualTo(10);
        assertThat(updated.breakRules()).isEmpty();
        assertThat(updated.overtimeRules()).hasSize(1);
        assertThat(updated.version()).isGreaterThan(created.version());
    }

    @Test
    void concurrent_update_with_stale_version_409() {
        var created = shiftService.create(fixedShiftWithTwoOtTiers());

        // First update genuinely changes the name so Hibernate bumps the version.
        var renamed = new ShiftDtos.ShiftRequest(
                "Day 9-5 renamed", ShiftType.FIXED, "#3b82f6",
                null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 1020, null)),
                List.of(), List.of(), List.of(), List.of(), null, null);
        var first = shiftService.update(created.id(), renamed);
        assertThat(first.version()).isGreaterThan(created.version());

        // A second update with the original (stale) version is rejected.
        var stale = new ShiftDtos.ShiftRequest(
                "Day 9-5 stale", ShiftType.FIXED, "#3b82f6",
                null, null, true, regId,
                List.of(new ShiftDtos.SegmentRequest(0, 540, 1020, null)),
                List.of(), List.of(), List.of(), List.of(),
                null, created.version());
        assertThatThrownBy(() -> shiftService.update(created.id(), stale))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("modified by another request");
    }
}
