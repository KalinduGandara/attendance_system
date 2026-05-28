package com.attendance.timecard.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RatedMinutesCalculatorTest {

    private final UUID code = UUID.randomUUID();

    @Test
    void rateOneIsIdentity() {
        int r = RatedMinutesCalculator.rated(60, code, Map.of(code, BigDecimal.ONE));
        assertThat(r).isEqualTo(60);
    }

    @Test
    void rateOnePointFiveMultiplies() {
        int r = RatedMinutesCalculator.rated(60, code, Map.of(code, new BigDecimal("1.50")));
        assertThat(r).isEqualTo(90);
    }

    @Test
    void halfEvenRoundsBankerStyle() {
        // 5 minutes × 0.5 = 2.5 → HALF_EVEN → 2
        int r = RatedMinutesCalculator.rated(5, code, Map.of(code, new BigDecimal("0.50")));
        assertThat(r).isEqualTo(2);
        // 7 minutes × 0.5 = 3.5 → HALF_EVEN → 4
        int r2 = RatedMinutesCalculator.rated(7, code, Map.of(code, new BigDecimal("0.50")));
        assertThat(r2).isEqualTo(4);
    }

    @Test
    void missingRateDefaultsToOne() {
        int r = RatedMinutesCalculator.rated(60, UUID.randomUUID(), Map.of());
        assertThat(r).isEqualTo(60);
    }
}
