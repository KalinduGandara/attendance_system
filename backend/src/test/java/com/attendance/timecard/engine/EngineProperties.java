package com.attendance.timecard.engine;

import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.engine.snapshots.PunchSnapshot;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.attendance.timecard.engine.EngineTestFixtures.ATTEND;
import static com.attendance.timecard.engine.EngineTestFixtures.NY;
import static com.attendance.timecard.engine.EngineTestFixtures.fixedNineToFive;
import static com.attendance.timecard.engine.EngineTestFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * jqwik property tests over the time-card engine. The invariants must hold
 * across thousands of random punch sequences, not just hand-picked fixtures.
 */
class EngineProperties {

    private static final LocalDate DAY = LocalDate.of(2026, 5, 28);

    @Property(tries = 200)
    void workedPlusBreakNeverExceedsCoveredWallClock(@ForAll("randomPunchSequence") List<PunchSnapshot> punches) {
        ShiftSnapshot shift = fixedNineToFive(ATTEND, List.of(), List.of(), List.of(), List.of());
        EngineInputs in = new EngineInputs(UUID.randomUUID(), DAY, NY,
                EngineInputs.ScheduledState.SCHEDULED, shift, punches, false, null,
                Map.of(ATTEND, BigDecimal.ONE));
        EngineOutput out = TimeCardCalculator.compute(in);

        long coveredMinutes = punches.stream()
                .map(PunchSnapshot::eventTimeUtc)
                .map(Instant::getEpochSecond)
                .collect(Collectors.collectingAndThen(Collectors.toList(), times -> {
                    if (times.isEmpty()) {
                        return 0L;
                    }
                    long min = times.stream().min(Long::compareTo).orElseThrow();
                    long max = times.stream().max(Long::compareTo).orElseThrow();
                    return (max - min) / 60;
                }));

        assertThat(out.workedMinutes()).as("workedMinutes is non-negative").isGreaterThanOrEqualTo(0);
        assertThat(out.breakMinutes()).as("breakMinutes is non-negative").isGreaterThanOrEqualTo(0);
        assertThat((long) out.workedMinutes() + out.breakMinutes())
                .as("worked + break ≤ wall-clock minutes covered")
                .isLessThanOrEqualTo(coveredMinutes);
    }

    @Property(tries = 200)
    void breakdownMinutesSumEqualsWorkedPlusPaidBreaks(
            @ForAll("randomPunchSequence") List<PunchSnapshot> punches) {
        ShiftSnapshot shift = fixedNineToFive(ATTEND, List.of(), List.of(), List.of(), List.of());
        EngineInputs in = new EngineInputs(UUID.randomUUID(), DAY, NY,
                EngineInputs.ScheduledState.SCHEDULED, shift, punches, false, null,
                Map.of(ATTEND, BigDecimal.ONE));
        EngineOutput out = TimeCardCalculator.compute(in);

        int sumMinutes = out.breakdowns().stream().mapToInt(EngineOutput.BreakdownLine::minutes).sum();
        assertThat(sumMinutes).isEqualTo(out.workedMinutes());
    }

    @Provide
    Arbitrary<List<PunchSnapshot>> randomPunchSequence() {
        Arbitrary<Integer> minutes = Arbitraries.integers().between(0, 1440);
        Arbitrary<PunchEventType> types = Arbitraries.of(PunchEventType.CHECK_IN, PunchEventType.CHECK_OUT);
        Arbitrary<PunchSnapshot> punch = Arbitraries.create(() -> new PunchSnapshot(
                UUID.randomUUID(),
                types.sample(),
                ts(DAY, minutes.sample() / 60, minutes.sample() % 60)));
        return punch.list().ofMinSize(0).ofMaxSize(10);
    }
}
