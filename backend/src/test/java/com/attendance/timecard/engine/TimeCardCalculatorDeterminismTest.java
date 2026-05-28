package com.attendance.timecard.engine;

import com.attendance.timecard.domain.PunchEventType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.attendance.timecard.engine.EngineTestFixtures.ATTEND;
import static com.attendance.timecard.engine.EngineTestFixtures.NY;
import static com.attendance.timecard.engine.EngineTestFixtures.OT_A;
import static com.attendance.timecard.engine.EngineTestFixtures.fixedNineToFive;
import static com.attendance.timecard.engine.EngineTestFixtures.punch;
import static com.attendance.timecard.engine.EngineTestFixtures.tier;
import static com.attendance.timecard.engine.EngineTestFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;

class TimeCardCalculatorDeterminismTest {

    private static final LocalDate DAY = LocalDate.of(2026, 5, 28);

    @Test
    void sameInputsProduceEqualOutputs() {
        UUID emp = UUID.randomUUID();
        var shift = fixedNineToFive(ATTEND,
                List.of(tier(0, 480, OT_A, null)),
                List.of(), List.of(), List.of());
        Map<UUID, BigDecimal> rates = Map.of(ATTEND, BigDecimal.ONE, OT_A, new BigDecimal("1.5"));
        // use deterministic ids so equality holds.
        UUID p1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID p2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var inputs = new EngineInputs(emp, DAY, NY, EngineInputs.ScheduledState.SCHEDULED, shift,
                List.of(punch(p1, PunchEventType.CHECK_IN, ts(DAY, 9, 0)),
                        punch(p2, PunchEventType.CHECK_OUT, ts(DAY, 18, 0))),
                false, null, rates);

        EngineOutput a = TimeCardCalculator.compute(inputs);
        EngineOutput b = TimeCardCalculator.compute(inputs);

        assertThat(a).isEqualTo(b);
        assertThat(a.workedMinutes()).isEqualTo(540);
        assertThat(a.overtimeMinutes()).isEqualTo(60);
    }
}
