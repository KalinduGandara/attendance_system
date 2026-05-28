package com.attendance.timecard.engine;

import com.attendance.timecard.engine.snapshots.ShiftSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Slices a day's total worked minutes into chronological tiers — attendance
 * code first, then OT-A, OT-B, ... per the shift's overtime rules.
 *
 * <p>Per SRS FR-4.4, the OT thresholds are absolute "after N minutes worked";
 * tier 1 starts the moment {@code afterMinutesWorked} is reached, and tier 2
 * starts at its own {@code afterMinutesWorked}. Each tier is optionally capped
 * by {@code maxMinutes}; minutes beyond an uncapped final tier still accrue to
 * it (and surface to the exception detector for UNAUTHORIZED_OT analysis).
 */
public final class OvertimeTierSlicer {

    private OvertimeTierSlicer() {
    }

    public static List<Slice> slice(int workedMinutes,
                                    UUID attendanceTimeCodeId,
                                    List<ShiftSnapshot.OvertimeTierSnapshot> tiers) {
        if (workedMinutes <= 0) {
            return List.of();
        }
        List<ShiftSnapshot.OvertimeTierSnapshot> sortedTiers = tiers.stream()
                .sorted(Comparator.comparingInt(ShiftSnapshot.OvertimeTierSnapshot::sequenceOrder))
                .toList();

        List<Slice> out = new ArrayList<>();
        int seq = 0;

        int remaining = workedMinutes;
        int allocated = 0;

        // Attendance slice — runs until the first OT tier's threshold (or all minutes).
        int firstOtAt = sortedTiers.isEmpty()
                ? Integer.MAX_VALUE
                : Math.max(0, sortedTiers.get(0).afterMinutesWorked());
        int attendanceMinutes = Math.min(remaining, firstOtAt);
        if (attendanceMinutes > 0) {
            out.add(new Slice(attendanceTimeCodeId, attendanceMinutes, seq++, false));
            allocated += attendanceMinutes;
            remaining -= attendanceMinutes;
        }

        // OT tiers in sequence.
        for (int i = 0; i < sortedTiers.size() && remaining > 0; i++) {
            ShiftSnapshot.OvertimeTierSnapshot tier = sortedTiers.get(i);
            int nextStart = i + 1 < sortedTiers.size()
                    ? sortedTiers.get(i + 1).afterMinutesWorked()
                    : Integer.MAX_VALUE;
            int tierCapByNext = Math.max(0, nextStart - tier.afterMinutesWorked());
            int tierCapByOwn = tier.maxMinutes() == null ? Integer.MAX_VALUE : tier.maxMinutes();
            int tierWindow = Math.min(tierCapByNext, tierCapByOwn);
            int minutesForTier = Math.min(remaining, tierWindow);
            if (minutesForTier > 0) {
                out.add(new Slice(tier.timeCodeId(), minutesForTier, seq++, true));
                allocated += minutesForTier;
                remaining -= minutesForTier;
            }
        }

        // Anything left after the last tier's cap is "unauthorized" overtime —
        // we still credit it to the last OT tier (so report totals balance) and
        // let the exception detector emit UNAUTHORIZED_OT. If there are no OT
        // tiers but remaining > 0, the engine had no overtime structure, so
        // remaining minutes go to the attendance code.
        if (remaining > 0) {
            if (sortedTiers.isEmpty()) {
                int idx = -1;
                for (int i = 0; i < out.size(); i++) {
                    if (out.get(i).timeCodeId().equals(attendanceTimeCodeId)) {
                        idx = i;
                        break;
                    }
                }
                if (idx >= 0) {
                    Slice s = out.get(idx);
                    out.set(idx, new Slice(s.timeCodeId(), s.minutes() + remaining, s.sequenceOrder(), s.overtime()));
                } else {
                    out.add(new Slice(attendanceTimeCodeId, remaining, seq, false));
                }
            } else {
                ShiftSnapshot.OvertimeTierSnapshot last = sortedTiers.get(sortedTiers.size() - 1);
                int idx = -1;
                for (int i = out.size() - 1; i >= 0; i--) {
                    if (out.get(i).timeCodeId().equals(last.timeCodeId()) && out.get(i).overtime()) {
                        idx = i;
                        break;
                    }
                }
                if (idx >= 0) {
                    Slice s = out.get(idx);
                    out.set(idx, new Slice(s.timeCodeId(), s.minutes() + remaining, s.sequenceOrder(), true));
                } else {
                    out.add(new Slice(last.timeCodeId(), remaining, seq, true));
                }
            }
            allocated += remaining;
        }

        assert allocated == workedMinutes : "tier slicer must preserve total minutes";
        return out;
    }

    public record Slice(UUID timeCodeId, int minutes, int sequenceOrder, boolean overtime) {
    }
}
