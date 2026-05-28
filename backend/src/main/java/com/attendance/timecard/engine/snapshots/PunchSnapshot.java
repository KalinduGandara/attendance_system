package com.attendance.timecard.engine.snapshots;

import com.attendance.timecard.domain.PunchEventType;

import java.time.Instant;
import java.util.UUID;

/** Immutable, JPA-free view of a punch event passed to the engine. */
public record PunchSnapshot(
        UUID id,
        PunchEventType eventType,
        Instant eventTimeUtc) {
}
