package com.attendance.timecard.engine;

import com.attendance.shift.domain.RoundingKind;
import com.attendance.shift.domain.RoundingMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.attendance.timecard.engine.EngineTestFixtures.round;
import static com.attendance.timecard.engine.EngineTestFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;

class RounderTest {

    private static final LocalDate DAY = LocalDate.of(2026, 5, 28);

    @Test
    void roundUpToNext15Minutes() {
        Instant t = ts(DAY, 9, 7);
        Instant rounded = Rounder.round(t, 15, RoundingMode.UP);
        assertThat(rounded).isEqualTo(ts(DAY, 9, 15));
    }

    @Test
    void roundDownToPrevious15Minutes() {
        Instant t = ts(DAY, 9, 7);
        Instant rounded = Rounder.round(t, 15, RoundingMode.DOWN);
        assertThat(rounded).isEqualTo(ts(DAY, 9, 0));
    }

    @Test
    void roundNearestPicksCloser() {
        assertThat(Rounder.round(ts(DAY, 9, 7), 15, RoundingMode.NEAREST))
                .isEqualTo(ts(DAY, 9, 0));
        assertThat(Rounder.round(ts(DAY, 9, 8), 15, RoundingMode.NEAREST))
                .isEqualTo(ts(DAY, 9, 15));
    }

    @Test
    void roundExactBoundaryIsIdempotent() {
        assertThat(Rounder.round(ts(DAY, 9, 0), 15, RoundingMode.UP)).isEqualTo(ts(DAY, 9, 0));
        assertThat(Rounder.round(ts(DAY, 9, 0), 15, RoundingMode.DOWN)).isEqualTo(ts(DAY, 9, 0));
        assertThat(Rounder.round(ts(DAY, 9, 0), 15, RoundingMode.NEAREST)).isEqualTo(ts(DAY, 9, 0));
    }

    @Test
    void punchInRoundUpAdvancesStartOnly() {
        Interval orig = new Interval(ts(DAY, 9, 7), ts(DAY, 17, 0));
        List<Interval> rounded = Rounder.applyRoundingRules(List.of(orig), List.of(
                round(RoundingKind.PUNCH_IN, 15, RoundingMode.UP)));
        assertThat(rounded).hasSize(1);
        assertThat(rounded.get(0).start()).isEqualTo(ts(DAY, 9, 15));
        assertThat(rounded.get(0).end()).isEqualTo(ts(DAY, 17, 0));
    }

    @Test
    void shiftKindRoundsFirstStartAndLastEndTogether() {
        Interval first = new Interval(ts(DAY, 8, 53), ts(DAY, 12, 0));
        Interval last = new Interval(ts(DAY, 13, 0), ts(DAY, 17, 7));
        List<Interval> rounded = Rounder.applyRoundingRules(List.of(first, last), List.of(
                round(RoundingKind.SHIFT, 15, RoundingMode.NEAREST)));
        assertThat(rounded.get(0).start()).isEqualTo(ts(DAY, 9, 0));
        assertThat(rounded.get(1).end()).isEqualTo(ts(DAY, 17, 0));
    }
}
