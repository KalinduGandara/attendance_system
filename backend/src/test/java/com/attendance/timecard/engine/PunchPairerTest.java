package com.attendance.timecard.engine;

import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.engine.snapshots.PunchSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.attendance.timecard.engine.EngineTestFixtures.punch;
import static com.attendance.timecard.engine.EngineTestFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;

class PunchPairerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 5, 28);

    @Test
    void pairsClassicInOut() {
        PunchPairer.PairingResult r = PunchPairer.pair(List.of(
                punch(PunchEventType.CHECK_IN, ts(DAY, 9, 0)),
                punch(PunchEventType.CHECK_OUT, ts(DAY, 17, 0))));

        assertThat(r.work()).hasSize(1);
        assertThat(r.work().get(0).durationMinutes()).isEqualTo(480);
        assertThat(r.breaks()).isEmpty();
        assertThat(r.missingIn()).isZero();
        assertThat(r.missingOut()).isZero();
    }

    @Test
    void emitsMissingOutForUnclosedCheckIn() {
        PunchPairer.PairingResult r = PunchPairer.pair(List.of(
                punch(PunchEventType.CHECK_IN, ts(DAY, 9, 0))));
        assertThat(r.work()).isEmpty();
        assertThat(r.missingOut()).isEqualTo(1);
    }

    @Test
    void emitsMissingInForOrphanCheckOut() {
        PunchPairer.PairingResult r = PunchPairer.pair(List.of(
                punch(PunchEventType.CHECK_OUT, ts(DAY, 17, 0))));
        assertThat(r.work()).isEmpty();
        assertThat(r.missingIn()).isEqualTo(1);
    }

    @Test
    void pairsBreakStartEndIntoBreakInterval() {
        PunchPairer.PairingResult r = PunchPairer.pair(List.of(
                punch(PunchEventType.CHECK_IN, ts(DAY, 9, 0)),
                punch(PunchEventType.BREAK_START, ts(DAY, 12, 0)),
                punch(PunchEventType.BREAK_END, ts(DAY, 12, 30)),
                punch(PunchEventType.CHECK_OUT, ts(DAY, 17, 0))));

        assertThat(r.work()).hasSize(1);
        assertThat(r.breaks()).hasSize(1);
        assertThat(r.breaks().get(0).durationMinutes()).isEqualTo(30);
    }

    @Test
    void stableSortByTimeAndThenId() {
        var sameTime = ts(DAY, 9, 0);
        java.util.UUID later = java.util.UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        java.util.UUID earlier = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        // Provide CHECK_OUT before CHECK_IN at the same time; with id ordering CHECK_IN (earlier UUID) wins.
        PunchPairer.PairingResult r = PunchPairer.pair(List.of(
                new PunchSnapshot(later, PunchEventType.CHECK_OUT, sameTime),
                new PunchSnapshot(earlier, PunchEventType.CHECK_IN, sameTime)));
        // CHECK_IN is paired against the CHECK_OUT, but both at the same time → zero-duration interval is dropped.
        assertThat(r.work()).isEmpty();
        assertThat(r.missingIn()).isEqualTo(1);
    }
}
