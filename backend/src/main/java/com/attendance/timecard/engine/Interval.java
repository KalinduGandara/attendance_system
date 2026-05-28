package com.attendance.timecard.engine;

import java.time.Duration;
import java.time.Instant;

/** Half-open [start, end) interval. */
public record Interval(Instant start, Instant end) {

    public Interval {
        if (start == null || end == null) {
            throw new IllegalArgumentException("interval bounds must be non-null");
        }
    }

    public int durationMinutes() {
        long m = Duration.between(start, end).toMinutes();
        return (int) Math.max(0, m);
    }

    public Interval withStart(Instant newStart) {
        return new Interval(newStart, end);
    }

    public Interval withEnd(Instant newEnd) {
        return new Interval(start, newEnd);
    }
}
