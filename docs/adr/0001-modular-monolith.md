# ADR-0001: Modular Monolith with Hexagonal Seams

**Status:** Accepted
**Date:** 2026-05-26

## Context
We're building a single-tenant Time & Attendance system. We need clean module boundaries because functional areas (ingestion, scheduling, reporting) will evolve at different rates, but we don't have the operational complexity budget or scale requirements that justify microservices.

We expect three areas to evolve: how punch events enter the system, which database vendor we run on, and how reports are exported.

## Decision
Build a **modular monolith**: one deployable backend, one frontend SPA, with strict package boundaries between functional modules. Modules expose their public APIs as service interfaces; cross-module access to entities, repositories, or private services is forbidden.

For the three known evolving areas, introduce **ports and adapters**:
- `PunchEventIngestionPort` for ingestion sources
- `DatabaseDialectPort` for vendor-specific SQL
- `ReportExporterPort` for report serialization

These are the only abstractions we build upfront. Other modules stay concrete until a second implementation is actually needed.

## Consequences

**Positive**
- Single artifact, single deploy, simple ops.
- Strong compile-time enforcement of module boundaries via package-private classes.
- The three known evolution points have explicit seams; future adapters slot in without touching the core.
- If a module ever needs to be extracted, the boundaries are already drawn.

**Negative**
- Discipline required: it's tempting to reach across modules. We mitigate with `archunit` tests in CI.
- A single deployment unit means a bad change in one module can crash the whole app. Acceptable for v1's traffic profile.

**Rejected alternatives**
- Microservices: too much operational overhead for a single-tenant on-prem product at this scale.
- Plain monolith with no internal boundaries: invites a big ball of mud.
- Full DDD with bounded contexts and event sourcing: over-engineered for the problem and would slow first delivery substantially.
