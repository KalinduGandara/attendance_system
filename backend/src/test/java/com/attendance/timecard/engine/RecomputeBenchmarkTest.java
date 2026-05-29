package com.attendance.timecard.engine;

import com.attendance.shift.domain.GraceKind;
import com.attendance.shift.domain.RoundingKind;
import com.attendance.shift.domain.RoundingMode;
import com.attendance.timecard.domain.PunchEventType;
import com.attendance.timecard.engine.snapshots.PunchSnapshot;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.attendance.timecard.engine.EngineTestFixtures.ATTEND;
import static com.attendance.timecard.engine.EngineTestFixtures.LUNCH;
import static com.attendance.timecard.engine.EngineTestFixtures.NY;
import static com.attendance.timecard.engine.EngineTestFixtures.OT_A;
import static com.attendance.timecard.engine.EngineTestFixtures.OT_B;
import static com.attendance.timecard.engine.EngineTestFixtures.autoDeductLunch;
import static com.attendance.timecard.engine.EngineTestFixtures.fixedNineToFive;
import static com.attendance.timecard.engine.EngineTestFixtures.grace;
import static com.attendance.timecard.engine.EngineTestFixtures.punch;
import static com.attendance.timecard.engine.EngineTestFixtures.round;
import static com.attendance.timecard.engine.EngineTestFixtures.tier;
import static com.attendance.timecard.engine.EngineTestFixtures.ts;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 10 hardening — performance guard for the time-card engine.
 *
 * <p>plan.md §10 sets a recompute SLO of p95 &lt; 100 ms per (employee, day).
 * That budget is end-to-end (DB round-trips included) and is exercised by the
 * k6 load-test harness in {@code perf/} against a running stack. This JUnit
 * benchmark isolates the <em>pure</em> {@link TimeCardCalculator} — the
 * deterministic core that dominates CPU cost — and asserts it stays far inside
 * the budget, so an accidental algorithmic regression (e.g. an O(n²) slip in a
 * subroutine) fails the build rather than only showing up under load.
 *
 * <p>The bound is deliberately generous (10 ms) to tolerate CI jitter; the
 * measured p95 is printed for trend visibility. A loaded fixture is used —
 * fixed shift, two OT tiers, an auto-deduct lunch, rounding and grace rules —
 * so every subroutine runs.
 */
class RecomputeBenchmarkTest {

    private static final LocalDate DAY = LocalDate.of(2026, 5, 28);
    private static final int WARMUP = 5_000;
    private static final int MEASURED = 20_000;
    private static final long P95_BUDGET_NANOS = 10_000_000L; // 10 ms

    @Test
    void pureEngineRecomputeStaysWellInsideTheP95Budget() {
        EngineInputs inputs = loadedFixture();

        // Warm up the JIT so we measure steady-state, not interpretation.
        for (int i = 0; i < WARMUP; i++) {
            TimeCardCalculator.compute(inputs);
        }

        long[] samples = new long[MEASURED];
        for (int i = 0; i < MEASURED; i++) {
            long t0 = System.nanoTime();
            EngineOutput out = TimeCardCalculator.compute(inputs);
            samples[i] = System.nanoTime() - t0;
            // Touch the result so the JIT can't elide the call.
            if (out.workedMinutes() < 0) {
                throw new AssertionError("unreachable");
            }
        }

        Arrays.sort(samples);
        long p50 = samples[(int) (MEASURED * 0.50)];
        long p95 = samples[(int) (MEASURED * 0.95)];
        long p99 = samples[(int) (MEASURED * 0.99)];
        System.out.printf(
                "[recompute-benchmark] pure engine over %,d runs: p50=%.3fms p95=%.3fms p99=%.3fms%n",
                MEASURED, p50 / 1e6, p95 / 1e6, p99 / 1e6);

        assertThat(p95)
                .as("pure-engine recompute p95 (%.3f ms) must stay well under the 100 ms end-to-end SLO",
                        p95 / 1e6)
                .isLessThan(P95_BUDGET_NANOS);
    }

    /** A representative non-trivial day: full punch pair, lunch, OT, rounding, grace. */
    private static EngineInputs loadedFixture() {
        ShiftSnapshot shift = fixedNineToFive(
                ATTEND,
                List.of(tier(1, 480, OT_A, 120), tier(2, 600, OT_B, null)),
                List.of(autoDeductLunch(30, 6)),
                List.of(round(RoundingKind.PUNCH_IN, 15, RoundingMode.NEAREST)),
                List.of(grace(GraceKind.LATE_IN, 5), grace(GraceKind.EARLY_OUT, 5)));

        // Arrive a few minutes late, leave into a second OT tier.
        List<PunchSnapshot> punches = List.of(
                punch(PunchEventType.CHECK_IN, ts(DAY, 9, 3)),
                punch(PunchEventType.CHECK_OUT, ts(DAY, 19, 30)));

        return new EngineInputs(
                UUID.randomUUID(), DAY, NY,
                EngineInputs.ScheduledState.SCHEDULED, shift, punches,
                false, null,
                Map.of(ATTEND, BigDecimal.ONE, OT_A, new BigDecimal("1.5"),
                        OT_B, new BigDecimal("2.0"), LUNCH, BigDecimal.ZERO));
    }
}
