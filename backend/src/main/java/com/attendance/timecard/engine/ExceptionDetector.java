package com.attendance.timecard.engine;

import com.attendance.exception.domain.ExceptionSeverity;
import com.attendance.exception.domain.ExceptionType;
import com.attendance.timecard.engine.EngineOutput.EmittedException;
import com.attendance.timecard.engine.snapshots.ShiftSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Walks the engine's intermediate results and emits the appropriate
 * exception events. Pure function; the orchestrator persists what it returns.
 */
public final class ExceptionDetector {

    private ExceptionDetector() {
    }

    public static List<EmittedException> detect(DetectionInputs in) {
        List<EmittedException> out = new ArrayList<>();

        if (in.missingIn() > 0) {
            out.add(new EmittedException(ExceptionType.MISSING_PUNCH_IN, ExceptionSeverity.WARN));
        }
        if (in.missingOut() > 0) {
            out.add(new EmittedException(ExceptionType.MISSING_PUNCH_OUT, ExceptionSeverity.WARN));
        }
        if (in.lateMinutes() > 0) {
            out.add(new EmittedException(ExceptionType.LATE_IN,
                    in.lateMinutes() > 30 ? ExceptionSeverity.WARN : ExceptionSeverity.INFO));
        }
        if (in.earlyOutMinutes() > 0) {
            out.add(new EmittedException(ExceptionType.EARLY_OUT,
                    in.earlyOutMinutes() > 30 ? ExceptionSeverity.WARN : ExceptionSeverity.INFO));
        }
        if (in.absentNoLeave()) {
            out.add(new EmittedException(ExceptionType.ABSENT_NO_LEAVE, ExceptionSeverity.CRITICAL));
        }
        if (in.orphanPunch()) {
            out.add(new EmittedException(ExceptionType.ORPHAN_PUNCH, ExceptionSeverity.INFO));
        }
        if (in.unauthorizedOtMinutes() > 0) {
            out.add(new EmittedException(ExceptionType.UNAUTHORIZED_OT, ExceptionSeverity.WARN));
        }
        return out;
    }

    /**
     * Computes how many worked minutes exceeded the final OT tier's cap.
     * Returns 0 when the final tier is uncapped or when there are no tiers.
     */
    public static int unauthorizedOt(int workedMinutes, List<ShiftSnapshot.OvertimeTierSnapshot> tiers) {
        if (tiers.isEmpty()) {
            return 0;
        }
        var sorted = tiers.stream()
                .sorted(Comparator.comparingInt(ShiftSnapshot.OvertimeTierSnapshot::sequenceOrder))
                .toList();
        var last = sorted.get(sorted.size() - 1);
        if (last.maxMinutes() == null) {
            return 0;
        }
        int allowed = last.afterMinutesWorked() + last.maxMinutes();
        return Math.max(0, workedMinutes - allowed);
    }

    public record DetectionInputs(
            int missingIn,
            int missingOut,
            int lateMinutes,
            int earlyOutMinutes,
            boolean absentNoLeave,
            boolean orphanPunch,
            int unauthorizedOtMinutes) {
    }
}
