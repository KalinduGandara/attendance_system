package com.attendance.timecard.engine;

import com.attendance.shift.domain.ShiftType;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.attendance.timecard.engine.EngineTestFixtures.ATTEND;
import static com.attendance.timecard.engine.EngineTestFixtures.NY;
import static com.attendance.timecard.engine.EngineTestFixtures.punch;
import static com.attendance.timecard.engine.EngineTestFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;

class FloatingShiftSelectorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 5, 28);

    private final ShiftSnapshot day = candidate("Day 9-5", 9 * 60, 17 * 60,
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private final ShiftSnapshot night = candidate("Night 18-2", 18 * 60, 26 * 60,
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));

    @Test
    void picksClosestStartToFirstCheckIn() {
        var pick = FloatingShiftSelector.select(DAY, NY,
                List.of(punch(PunchEventType.CHECK_IN, ts(DAY, 17, 50))),
                List.of(day, night));
        assertThat(pick).isPresent();
        assertThat(pick.get().name()).isEqualTo("Night 18-2");
    }

    @Test
    void breaksTiesByIdLexicographically() {
        ShiftSnapshot earlyId = candidate("Day 9-5", 9 * 60, 17 * 60,
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        ShiftSnapshot lateId = candidate("Other 9-5", 9 * 60, 17 * 60,
                UUID.fromString("ffffffff-ffff-ffff-ffff-fffffffffff0"));
        var pick = FloatingShiftSelector.select(DAY, NY,
                List.of(punch(PunchEventType.CHECK_IN, ts(DAY, 9, 0))),
                List.of(lateId, earlyId));
        assertThat(pick.get().id()).isEqualTo(earlyId.id());
    }

    @Test
    void noCheckInReturnsEmpty() {
        var pick = FloatingShiftSelector.select(DAY, NY, List.of(), List.of(day, night));
        assertThat(pick).isEmpty();
    }

    private ShiftSnapshot candidate(String name, int startMin, int endMin, UUID id) {
        return new ShiftSnapshot(
                id, name, ShiftType.FIXED, ATTEND,
                List.of(new ShiftSnapshot.SegmentSnapshot(0, startMin, endMin, null)),
                List.of(), List.of(), List.of(), List.of(),
                Set.of());
    }
}
