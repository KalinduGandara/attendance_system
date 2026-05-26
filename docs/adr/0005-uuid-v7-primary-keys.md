# ADR-0005: UUID v7 Primary Keys

**Status:** Accepted
**Date:** 2026-05-26

## Context
We need a primary key strategy that:
- Is safe to expose in URLs and APIs without leaking row counts or business volume.
- Doesn't require coordination across services (we may distribute later).
- Plays well with B-tree indexes (sequential inserts) for performance.
- Is portable across MariaDB / PostgreSQL / MSSQL.

The classic options:
- BIGINT auto-increment: small, fast joins, but leaks information and needs a public-facing slug.
- Random UUID v4: opaque, but random inserts trash B-tree locality on write-heavy tables (we have `punch_event`).
- UUID v7: time-ordered random UUID; inserts stay sequential; same opacity as v4.

## Decision
**UUID v7** for every primary key.

- **Storage**: `BINARY(16)` in MariaDB (and equivalent in PostgreSQL `uuid` / MSSQL `uniqueidentifier`).
- **Generation**: client-side (application code, before insert) via a small utility. Allows entity objects to have an ID immediately after `new`, simplifies child-entity wiring before save.
- **API representation**: canonical hyphenated lowercase string.
- **JPA**: `@Convert` to map `UUID` ↔ `byte[]`.

## Consequences

**Positive**
- Time-ordered ⇒ B-tree-friendly inserts. Critical for `punch_event` and `audit_event` which are write-heavy.
- Opaque to clients — no row count leakage, safe in URLs.
- Generated in app code ⇒ no DB round-trip just to get an ID.
- Portable across all three target DBs.

**Negative**
- 16 bytes vs 8 for BIGINT — slightly larger indexes. Negligible at our scale.
- Less ergonomic when debugging (`SELECT * FROM x WHERE id=UNHEX('…')`) — we'll add a helper view in dev that decodes IDs.

**Implementation notes**
- `com.attendance.common.id.UuidV7Generator` is the only place that creates UUIDs.
- All entity `@Id` fields are `private UUID id` typed; setters package-private.
- JSON serializers always emit canonical string.
- The dialect port abstracts `UNHEX()` / `cast(? as uuid)` differences if we ever need raw SQL on IDs.

**Rejected alternatives**
- BIGINT: ruled out by URL safety requirement.
- UUID v4: ruled out by insert performance on `punch_event`.
- Snowflake IDs: would need a coordination service or worker-id management. Not justified.
