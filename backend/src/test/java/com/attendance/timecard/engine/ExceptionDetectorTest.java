package com.attendance.timecard.engine;

import com.attendance.exception.domain.ExceptionType;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.attendance.timecard.engine.EngineTestFixtures.OT_A;
import static com.attendance.timecard.engine.EngineTestFixtures.tier;
import static org.assertj.core.api.Assertions.assertThat;

class ExceptionDetectorTest {

    @Test
    void missingPunchInIsWarn() {
        var out = ExceptionDetector.detect(new ExceptionDetector.DetectionInputs(
                1, 0, 0, 0, false, false, 0));
        assertThat(out).hasSize(1);
        assertThat(out.get(0).type()).isEqualTo(ExceptionType.MISSING_PUNCH_IN);
    }

    @Test
    void lateInUnder30MinIsInfo() {
        var out = ExceptionDetector.detect(new ExceptionDetector.DetectionInputs(
                0, 0, 15, 0, false, false, 0));
        assertThat(out.get(0).severity().name()).isEqualTo("INFO");
    }

    @Test
    void lateInOver30MinIsWarn() {
        var out = ExceptionDetector.detect(new ExceptionDetector.DetectionInputs(
                0, 0, 45, 0, false, false, 0));
        assertThat(out.get(0).severity().name()).isEqualTo("WARN");
    }

    @Test
    void absentNoLeaveIsCritical() {
        var out = ExceptionDetector.detect(new ExceptionDetector.DetectionInputs(
                0, 0, 0, 0, true, false, 0));
        assertThat(out.get(0).type()).isEqualTo(ExceptionType.ABSENT_NO_LEAVE);
        assertThat(out.get(0).severity().name()).isEqualTo("CRITICAL");
    }

    @Test
    void unauthorizedOtComputedFromTopTierCap() {
        // tier 0 starts at 480, cap 120 → allowed up to 600.
        int unauthorized = ExceptionDetector.unauthorizedOt(660, List.of(tier(0, 480, OT_A, 120)));
        assertThat(unauthorized).isEqualTo(60);
    }

    @Test
    void unauthorizedOtZeroWhenUncappedTier() {
        int unauthorized = ExceptionDetector.unauthorizedOt(1200, List.of(tier(0, 480, OT_A, null)));
        assertThat(unauthorized).isZero();
    }

    @Test
    void unauthorizedOtZeroWhenNoTiers() {
        int unauthorized = ExceptionDetector.unauthorizedOt(1200, List.<ShiftSnapshot.OvertimeTierSnapshot>of());
        assertThat(unauthorized).isZero();
    }
}
