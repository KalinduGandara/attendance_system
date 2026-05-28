package com.attendance.timecard.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.attendance.timecard.engine.EngineTestFixtures.ATTEND;
import static com.attendance.timecard.engine.EngineTestFixtures.OT_A;
import static com.attendance.timecard.engine.EngineTestFixtures.OT_B;
import static com.attendance.timecard.engine.EngineTestFixtures.tier;
import static org.assertj.core.api.Assertions.assertThat;

class OvertimeTierSlicerTest {

    @Test
    void noTiersAllocatesEverythingToAttendance() {
        var slices = OvertimeTierSlicer.slice(480, ATTEND, List.of());
        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).minutes()).isEqualTo(480);
        assertThat(slices.get(0).timeCodeId()).isEqualTo(ATTEND);
        assertThat(slices.get(0).overtime()).isFalse();
    }

    @Test
    void singleTierKicksInAfterThreshold() {
        var slices = OvertimeTierSlicer.slice(540, ATTEND, List.of(
                tier(0, 480, OT_A, null)));
        assertThat(slices).hasSize(2);
        assertThat(slices.get(0).minutes()).isEqualTo(480);
        assertThat(slices.get(0).timeCodeId()).isEqualTo(ATTEND);
        assertThat(slices.get(1).minutes()).isEqualTo(60);
        assertThat(slices.get(1).timeCodeId()).isEqualTo(OT_A);
        assertThat(slices.get(1).overtime()).isTrue();
    }

    @Test
    void twoTiersCapByNextTierStart() {
        var slices = OvertimeTierSlicer.slice(720, ATTEND, List.of(
                tier(0, 480, OT_A, null),
                tier(1, 660, OT_B, null)));
        assertThat(slices).hasSize(3);
        assertThat(slices.get(0).minutes()).isEqualTo(480);
        assertThat(slices.get(1).minutes()).isEqualTo(180);
        assertThat(slices.get(1).timeCodeId()).isEqualTo(OT_A);
        assertThat(slices.get(2).minutes()).isEqualTo(60);
        assertThat(slices.get(2).timeCodeId()).isEqualTo(OT_B);
    }

    @Test
    void tierMaxMinutesCapsTheSlice() {
        var slices = OvertimeTierSlicer.slice(720, ATTEND, List.of(
                tier(0, 480, OT_A, 120)));
        // attendance 480 + OT-A 120 = 600; remaining 120 stays on OT-A (unauthorized OT).
        assertThat(slices.stream().filter(s -> s.timeCodeId().equals(OT_A))
                .mapToInt(OvertimeTierSlicer.Slice::minutes).sum()).isEqualTo(240);
    }

    @Test
    void zeroMinutesProducesNoSlices() {
        assertThat(OvertimeTierSlicer.slice(0, ATTEND, List.of())).isEmpty();
    }
}
