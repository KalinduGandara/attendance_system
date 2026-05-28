package com.attendance.timecard.engine;

import com.attendance.timecard.domain.DailyTimeCardStatus;

import java.util.List;
import java.util.UUID;

final class EngineOutputs {

    private EngineOutputs() {
    }

    static EngineOutput empty(DailyTimeCardStatus status, UUID resolvedShiftId) {
        return new EngineOutput(
                status,
                resolvedShiftId,
                null, null, null, null,
                0, 0, 0, 0, 0,
                List.of(),
                List.of(),
                null);
    }
}
