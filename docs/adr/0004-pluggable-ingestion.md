# ADR-0004: Pluggable Punch Event Ingestion

**Status:** Accepted
**Date:** 2026-05-26

## Context
Today the system is standalone — punch events come in via REST from a UI or simulated devices. Tomorrow we'll want to plug in:
- Real biometric devices via vendor SDKs.
- External systems' databases (sync from another HR/T&A product).
- CSV imports.
- Webhook receivers from third-party time-clocking apps.

If we hard-code the REST flow, every new source becomes invasive surgery on the punch pipeline.

## Decision
Introduce a **`PunchEventIngestionPort`** interface that all sources implement:

```java
public interface PunchEventIngestionPort {
    IngestionResult ingest(IngestionSource source, PunchEventBatch batch);
}
```

- **Idempotency** is enforced inside the port's default implementation, keyed on `(source_id, external_event_id)`. Adapters don't have to worry about replay.
- **Credential resolution** is shared: adapters submit raw credential values; the port resolves them to employees and stores unresolved events for reconciliation.
- **Domain event** `PunchEventIngested` is published after persistence; downstream concerns (time-card recompute) subscribe.
- **Configuration** lives in the `ingestion_source` table; an admin UI manages sources, including API key rotation for REST sources.

**First adapter (Phase 5)**: `RestIngestionAdapter` accepting `POST /api/v1/ingestion/punches`.

**Future adapters**: `DeviceSdkAdapter`, `ExternalDbSyncAdapter`, `CsvImportAdapter`. Each is a Spring `@Component` discovered at startup.

## Consequences

**Positive**
- Adding a source means writing one adapter class — no changes to the time-card engine, no schema changes.
- Idempotency and credential resolution are written once and shared.
- The admin UI naturally surfaces each source's health (`last_event_at`, `events_total`, `events_rejected`).

**Negative**
- A layer of indirection where most products would just have a REST controller. Justified by the explicit roadmap of multiple sources.
- Sources with very different ingestion semantics (e.g. bidirectional sync) may strain the abstraction. We accept that and will refactor if/when that comes up.

**Rejected alternatives**
- A single REST endpoint with vendor-specific paths: works for the next two sources, then becomes spaghetti.
- An event queue (RabbitMQ / Kafka) between adapters and the core: more moving parts than the volume justifies. The seam itself is what matters — we can drop a queue behind it later without changing the port.
