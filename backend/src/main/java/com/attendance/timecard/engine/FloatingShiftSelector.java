package com.attendance.timecard.engine;

import com.attendance.timecard.engine.snapshots.PunchSnapshot;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.attendance.timecard.domain.PunchEventType;

/**
 * For FLOATING shifts: picks the candidate whose first segment's start time
 * is closest to the first CHECK_IN of the day. Ties are broken by the smaller
 * candidate id so selection stays deterministic.
 */
public final class FloatingShiftSelector {

    private FloatingShiftSelector() {
    }

    public static Optional<ShiftSnapshot> select(LocalDate workDate,
                                                 ZoneId zone,
                                                 List<PunchSnapshot> punches,
                                                 List<ShiftSnapshot> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Optional<Instant> firstIn = punches.stream()
                .filter(p -> p.eventType() == PunchEventType.CHECK_IN)
                .map(PunchSnapshot::eventTimeUtc)
                .min(Comparator.naturalOrder());
        if (firstIn.isEmpty()) {
            return Optional.empty();
        }
        Instant target = firstIn.get();

        return candidates.stream()
                .min(Comparator.<ShiftSnapshot, Long>comparing(c -> distanceFromStart(c, workDate, zone, target))
                        .thenComparing(c -> c.id().toString()));
    }

    private static long distanceFromStart(ShiftSnapshot shift,
                                           LocalDate workDate,
                                           ZoneId zone,
                                           Instant firstIn) {
        Optional<ScheduleAnchor.Window> w = ScheduleAnchor.resolve(workDate, zone, shift);
        if (w.isEmpty()) {
            return Long.MAX_VALUE;
        }
        return Math.abs(Duration.between(w.get().start(), firstIn).toMinutes());
    }
}
