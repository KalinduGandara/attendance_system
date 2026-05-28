package com.attendance.timecard.engine;

import com.attendance.timecard.engine.snapshots.ShiftSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Computes the scheduled UTC start / end of a shift on a given work-date in
 * the employee's timezone. Segments are interpreted day-relative; a segment
 * whose {@code endMinuteOfDay} exceeds 1440 crosses midnight.
 *
 * <p>The "scheduled window" is the envelope of all segments — earliest start
 * to latest end. Used by the grace applier and exception detector.
 */
public final class ScheduleAnchor {

    private ScheduleAnchor() {
    }

    public static Optional<Window> resolve(LocalDate workDate, ZoneId zone, ShiftSnapshot shift) {
        if (shift == null || shift.segments().isEmpty()) {
            return Optional.empty();
        }
        List<ShiftSnapshot.SegmentSnapshot> segs = shift.segments();
        int earliestStart = segs.stream().mapToInt(ShiftSnapshot.SegmentSnapshot::startMinuteOfDay).min().orElseThrow();
        int latestEnd = segs.stream().mapToInt(ShiftSnapshot.SegmentSnapshot::endMinuteOfDay).max().orElseThrow();
        Instant start = atMinuteOfDay(workDate, zone, earliestStart);
        Instant end = atMinuteOfDay(workDate, zone, latestEnd);
        return Optional.of(new Window(start, end));
    }

    private static Instant atMinuteOfDay(LocalDate date, ZoneId zone, int minute) {
        int extraDays = minute / 1440;
        int withinDay = minute % 1440;
        LocalTime tod = LocalTime.of(withinDay / 60, withinDay % 60);
        return date.plusDays(extraDays).atTime(tod).atZone(zone).toInstant();
    }

    public record Window(Instant start, Instant end) {

        public int durationMinutes() {
            return (int) Math.max(0, Duration.between(start, end).toMinutes());
        }
    }
}
