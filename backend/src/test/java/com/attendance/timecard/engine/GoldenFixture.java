package com.attendance.timecard.engine;

import java.util.List;
import java.util.Map;

/**
 * Plain-shape record loaded from {@code fixtures/timecard/*.yml}. The loader
 * translates a {@code FixtureFile} into {@link EngineInputs} and validates
 * the engine's output against {@code expected}.
 *
 * <p>Time codes are referenced by short token (ATTEND / OT_A / OT_B / LUNCH /
 * LEAVE) so YAML stays human-editable; tokens map to fixed UUIDs in
 * {@link EngineTestFixtures}.
 */
public final class GoldenFixture {

    private GoldenFixture() {
    }

    public record FixtureFile(
            String name,
            String zone,
            String workDate,
            Boolean holiday,
            String scheduledState,
            LeaveSpec leave,
            ShiftSpec shift,
            List<PunchSpec> punches,
            Map<String, String> rates,
            ExpectedSpec expected) {
    }

    public record LeaveSpec(String timeCode, boolean halfDay) {
    }

    public record ShiftSpec(
            String type,
            String attendanceTimeCode,
            List<SegmentSpec> segments,
            List<RoundingSpec> rounding,
            List<GraceSpec> grace,
            List<BreakSpec> breaks,
            List<OvertimeSpec> overtime) {
    }

    public record SegmentSpec(int startMinute, int endMinute, Integer requiredMinutes) {
    }

    public record RoundingSpec(String kind, int unit, String mode) {
    }

    public record GraceSpec(String kind, int minutes) {
    }

    public record BreakSpec(String kind, int durationMinutes,
                            Integer afterHoursWorked, Boolean paid, String timeCode) {
    }

    public record OvertimeSpec(int sequence, int afterMinutes, String timeCode, Integer maxMinutes) {
    }

    public record PunchSpec(String type, String time) {
    }

    public record ExpectedSpec(
            String status,
            Integer workedMinutes,
            Integer breakMinutes,
            Integer overtimeMinutes,
            Integer lateMinutes,
            Integer earlyOutMinutes,
            List<String> breakdownCodes,
            List<String> exceptions) {
    }
}
