package com.attendance.timecard.engine;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.attendance.timecard.engine.EngineTestFixtures.autoDeductLunch;
import static com.attendance.timecard.engine.EngineTestFixtures.punchTracked;
import static com.attendance.timecard.engine.EngineTestFixtures.ts;
import static com.attendance.timecard.engine.EngineTestFixtures.LUNCH;
import static org.assertj.core.api.Assertions.assertThat;

class BreakDeducerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 5, 28);

    @Test
    void autoDeductSubtractsLunchOnceAfterThreshold() {
        List<Interval> work = List.of(new Interval(ts(DAY, 9, 0), ts(DAY, 17, 0))); // 480
        var r = BreakDeducer.deduce(work, List.of(), List.of(autoDeductLunch(30, 360)));
        assertThat(r.workedMinutes()).isEqualTo(480 - 30);
        assertThat(r.breakMinutes()).isEqualTo(30);
    }

    @Test
    void autoDeductSkippedBelowThreshold() {
        List<Interval> work = List.of(new Interval(ts(DAY, 9, 0), ts(DAY, 13, 0))); // 240 < 360
        var r = BreakDeducer.deduce(work, List.of(), List.of(autoDeductLunch(30, 360)));
        assertThat(r.workedMinutes()).isEqualTo(240);
        assertThat(r.breakMinutes()).isZero();
    }

    @Test
    void unpaidTrackedBreaksDeductFromWork() {
        List<Interval> work = List.of(new Interval(ts(DAY, 9, 0), ts(DAY, 17, 0)));
        List<Interval> breaks = List.of(new Interval(ts(DAY, 12, 0), ts(DAY, 12, 30)));
        var r = BreakDeducer.deduce(work, breaks, List.of(punchTracked(false, null)));
        assertThat(r.workedMinutes()).isEqualTo(450);
        assertThat(r.breakMinutes()).isEqualTo(30);
    }

    @Test
    void paidTrackedBreaksDoNotDeductWorkButCreditTimeCode() {
        List<Interval> work = List.of(new Interval(ts(DAY, 9, 0), ts(DAY, 17, 0)));
        List<Interval> breaks = List.of(new Interval(ts(DAY, 15, 0), ts(DAY, 15, 15)));
        var r = BreakDeducer.deduce(work, breaks, List.of(punchTracked(true, LUNCH)));
        assertThat(r.workedMinutes()).isEqualTo(480);
        assertThat(r.breakMinutes()).isEqualTo(15);
        assertThat(r.paidBreakAttributions()).hasSize(1);
        assertThat(r.paidBreakAttributions().get(0).minutes()).isEqualTo(15);
    }

    @Test
    void noBreakRulesLeavesWorkUntouched() {
        List<Interval> work = List.of(new Interval(ts(DAY, 9, 0), ts(DAY, 17, 0)));
        var r = BreakDeducer.deduce(work, List.of(), List.of());
        assertThat(r.workedMinutes()).isEqualTo(480);
        assertThat(r.breakMinutes()).isZero();
    }
}
