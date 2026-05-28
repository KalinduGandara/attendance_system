package com.attendance.timecard.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Multiplies a slice's minutes by the slice's time-code rate, rounding
 * HALF_EVEN to an integer minute count. Banker's rounding avoids cumulative
 * bias across many small slices in a reporting window.
 */
public final class RatedMinutesCalculator {

    private RatedMinutesCalculator() {
    }

    public static int rated(int minutes, UUID timeCodeId, Map<UUID, BigDecimal> rates) {
        BigDecimal rate = rates.getOrDefault(timeCodeId, BigDecimal.ONE);
        BigDecimal product = BigDecimal.valueOf(minutes).multiply(rate);
        return product.setScale(0, RoundingMode.HALF_EVEN).intValueExact();
    }
}
