package com.attendance.ingestion.service;

import com.attendance.ingestion.web.PunchIngestionDtos;

import java.util.UUID;

/**
 * Architecture seam (ADR 0004): everything that wants to inject punch events
 * goes through this port. The first adapter is the REST controller; future
 * adapters (device SDK, CSV importer, external-DB sync) implement the same
 * contract.
 */
public interface PunchEventIngestionPort {

    PunchIngestionDtos.IngestionResponse ingest(UUID authenticatedSourceId,
                                                String idempotencyKey,
                                                PunchIngestionDtos.PunchBatchRequest batch);
}
