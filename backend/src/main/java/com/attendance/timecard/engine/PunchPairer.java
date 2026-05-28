package com.attendance.timecard.engine;

import com.attendance.timecard.engine.snapshots.PunchSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Folds a chronological list of punch events into work and break intervals,
 * tracking unmatched CHECK_IN / CHECK_OUT events so the exception detector can
 * surface them.
 *
 * <p>Stateless and side-effect-free.
 */
public final class PunchPairer {

    private PunchPairer() {
    }

    public static PairingResult pair(List<PunchSnapshot> punches) {
        List<PunchSnapshot> sorted = punches.stream()
                .sorted(Comparator.comparing(PunchSnapshot::eventTimeUtc)
                        .thenComparing(PunchSnapshot::id))
                .toList();

        List<Interval> work = new ArrayList<>();
        List<Interval> breaks = new ArrayList<>();
        int missingIn = 0;
        int missingOut = 0;

        java.time.Instant workOpen = null;
        java.time.Instant breakOpen = null;

        for (PunchSnapshot p : sorted) {
            switch (p.eventType()) {
                case CHECK_IN -> {
                    if (workOpen != null) {
                        // previous CHECK_IN never closed
                        missingOut++;
                    }
                    workOpen = p.eventTimeUtc();
                }
                case CHECK_OUT -> {
                    if (workOpen == null) {
                        missingIn++;
                    } else if (!p.eventTimeUtc().isAfter(workOpen)) {
                        // ill-ordered: ignore the pair, treat as missing
                        missingIn++;
                        workOpen = null;
                    } else {
                        work.add(new Interval(workOpen, p.eventTimeUtc()));
                        workOpen = null;
                    }
                }
                case BREAK_START -> breakOpen = p.eventTimeUtc();
                case BREAK_END -> {
                    if (breakOpen != null && p.eventTimeUtc().isAfter(breakOpen)) {
                        breaks.add(new Interval(breakOpen, p.eventTimeUtc()));
                    }
                    breakOpen = null;
                }
            }
        }
        if (workOpen != null) {
            // unmatched CHECK_IN at end of day
            missingOut++;
        }

        return new PairingResult(work, breaks, missingIn, missingOut);
    }

    public record PairingResult(List<Interval> work,
                                List<Interval> breaks,
                                int missingIn,
                                int missingOut) {
    }
}
