package com.attendance.timecard.engine;

import com.attendance.shift.domain.GraceKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.attendance.timecard.engine.EngineTestFixtures.grace;
import static com.attendance.timecard.engine.EngineTestFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;

class GraceApplierTest {

    private static final LocalDate DAY = LocalDate.of(2026, 5, 28);

    private final ScheduleAnchor.Window scheduled =
            new ScheduleAnchor.Window(ts(DAY, 9, 0), ts(DAY, 17, 0));

    @Test
    void lateArrivalWithinGraceSnapsToScheduled() {
        Interval i = new Interval(ts(DAY, 9, 5), ts(DAY, 17, 0));
        var r = GraceApplier.apply(List.of(i), scheduled, List.of(grace(GraceKind.LATE_IN, 10)));
        assertThat(r.intervals().get(0).start()).isEqualTo(ts(DAY, 9, 0));
        assertThat(r.lateMinutes()).isZero();
    }

    @Test
    void lateArrivalBeyondGraceCountsAsLateMinutes() {
        Interval i = new Interval(ts(DAY, 9, 20), ts(DAY, 17, 0));
        var r = GraceApplier.apply(List.of(i), scheduled, List.of(grace(GraceKind.LATE_IN, 10)));
        assertThat(r.intervals().get(0).start()).isEqualTo(ts(DAY, 9, 20));
        assertThat(r.lateMinutes()).isEqualTo(20);
    }

    @Test
    void earlyOutWithinGraceSnapsToScheduled() {
        Interval i = new Interval(ts(DAY, 9, 0), ts(DAY, 16, 55));
        var r = GraceApplier.apply(List.of(i), scheduled, List.of(grace(GraceKind.EARLY_OUT, 10)));
        assertThat(r.intervals().get(0).end()).isEqualTo(ts(DAY, 17, 0));
        assertThat(r.earlyOutMinutes()).isZero();
    }

    @Test
    void earlyOutBeyondGraceCountsAsEarlyMinutes() {
        Interval i = new Interval(ts(DAY, 9, 0), ts(DAY, 16, 30));
        var r = GraceApplier.apply(List.of(i), scheduled, List.of(grace(GraceKind.EARLY_OUT, 10)));
        assertThat(r.intervals().get(0).end()).isEqualTo(ts(DAY, 16, 30));
        assertThat(r.earlyOutMinutes()).isEqualTo(30);
    }

    @Test
    void noGraceRulesMeansNoSnap() {
        Interval i = new Interval(ts(DAY, 9, 5), ts(DAY, 16, 55));
        var r = GraceApplier.apply(List.of(i), scheduled, List.of());
        assertThat(r.intervals().get(0).start()).isEqualTo(ts(DAY, 9, 5));
        assertThat(r.intervals().get(0).end()).isEqualTo(ts(DAY, 16, 55));
        assertThat(r.lateMinutes()).isEqualTo(5);
        assertThat(r.earlyOutMinutes()).isEqualTo(5);
    }
}
