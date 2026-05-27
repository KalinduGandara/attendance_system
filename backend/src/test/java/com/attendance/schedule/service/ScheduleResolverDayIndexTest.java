package com.attendance.schedule.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit test for the cycle-day arithmetic — no Spring context needed.
 */
class ScheduleResolverDayIndexTest {

    @Test
    void same_day_is_zero() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        assertThat(ScheduleResolver.dayIndexFor(start, start, 7)).isEqualTo(0);
    }

    @Test
    void weekly_cycle_wraps_at_seven() {
        LocalDate start = LocalDate.of(2026, 6, 1); // Monday
        for (int i = 0; i < 14; i++) {
            assertThat(ScheduleResolver.dayIndexFor(start, start.plusDays(i), 7))
                    .as("day %d", i)
                    .isEqualTo(i % 7);
        }
    }

    @Test
    void fortnight_cycle_wraps_at_fourteen() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        assertThat(ScheduleResolver.dayIndexFor(start, start.plusDays(0), 14)).isEqualTo(0);
        assertThat(ScheduleResolver.dayIndexFor(start, start.plusDays(13), 14)).isEqualTo(13);
        assertThat(ScheduleResolver.dayIndexFor(start, start.plusDays(14), 14)).isEqualTo(0);
        assertThat(ScheduleResolver.dayIndexFor(start, start.plusDays(27), 14)).isEqualTo(13);
    }

    @Test
    void daily_cycle_is_always_zero() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        for (int i = 0; i < 30; i++) {
            assertThat(ScheduleResolver.dayIndexFor(start, start.plusDays(i), 1)).isEqualTo(0);
        }
    }

    @Test
    void dst_spring_forward_does_not_skip_a_day() {
        // US spring-forward 2026-03-08; cycle in plain LocalDate arithmetic
        // should still increment by exactly one day per calendar day.
        LocalDate start = LocalDate.of(2026, 3, 2); // Monday
        assertThat(ScheduleResolver.dayIndexFor(start, LocalDate.of(2026, 3, 8), 7))
                .isEqualTo(6); // Sunday is index 6
        assertThat(ScheduleResolver.dayIndexFor(start, LocalDate.of(2026, 3, 9), 7))
                .isEqualTo(0); // next Monday wraps
    }
}
