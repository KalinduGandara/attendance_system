package com.attendance.timecard.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Application event published after a punch event has been persisted and
 * resolved to an employee. The {@link TimeCardRecomputeListener} subscribes
 * to trigger a recompute of the affected day.
 */
public record PunchEventIngestedEvent(UUID punchEventId,
                                      UUID employeeId,
                                      Instant eventTimeUtc) {
}
