# ADR-0007: Time Card Computation Is a Pure Function

**Status:** Accepted
**Date:** 2026-05-26

## Context
Computing a daily time card is the core domain logic of the product. It must:
- Apply complex, configurable rules (rounding, grace periods, breaks, tiered OT, floating-shift selection, leaves, holidays).
- Be **correct** — payroll consequences for getting it wrong.
- Be **auditable** — we need to explain how we arrived at any given number.
- Be **reproducible** — replaying yesterday's data should yield yesterday's result, unless rules changed (in which case we want the new result).
- Be **testable** at the unit level for every rule combination.

A time-card engine that calls repositories mid-computation is hard to test, hard to reason about, and hard to replay.

## Decision
The time-card calculator is a **pure function**:

```java
DailyTimeCard compute(
    Employee employee,
    LocalDate date,
    ResolvedSchedule schedule,
    List<PunchEvent> punches,
    List<Holiday> holidays,
    List<LeaveRequest> leaves,
    OrgSettings settings
);
```

- **No I/O inside.** No repository calls, no HTTP, no clock reads (the clock is part of `settings`).
- **No side effects.** It returns a `DailyTimeCard` value object; persisting is the caller's job.
- **Deterministic.** Same inputs always produce the same output, byte-for-byte.
- **Composed of small, named subroutines** (`PunchPairer`, `Rounder`, `GraceApplier`, `BreakDeducer`, `OvertimeTierSlicer`, `RatedMinutesCalculator`, `ExceptionDetector`) — each independently testable.
- **Orchestration is separate.** `TimeCardRecomputeService` is the impure caller that loads inputs from repositories, invokes `compute()`, and persists the result.

## Consequences

**Positive**
- Trivial to unit-test: feed in YAML fixtures, assert output. We can have hundreds of scenarios in CI without a DB.
- Replayable: rerun the engine with new rules on old data to assess impact before rollout.
- Easy to reason about — no hidden dependencies, no test order coupling.
- Performance: no I/O in a hot loop means the engine is genuinely fast; recompute fan-out (a rule change affecting thousands of employee-days) becomes a parallelization problem, not an I/O problem.

**Negative**
- Caller must marshal all inputs upfront. For a single recompute that's cheap; for batch recomputes we'll need a smart loader that hydrates inputs once for many days.
- Refactor cost if a new requirement legitimately needs cross-day context (e.g. weekly OT cap). Mitigation: the function signature can grow — `compute` becomes `computeRange` if needed, still pure.

**Verification**
- Golden test suite under `backend/src/test/resources/fixtures/timecard/`. Each fixture is a YAML file with `inputs:` and `expected:` sections. The test loader runs every file.
- Property-based tests (jqwik) for cross-rule invariants: `workedMinutes + breakMinutes ≤ wallClockMinutesCovered`, `sum(breakdown.minutes) == workedMinutes`, …
- Idempotency / determinism tests: compute twice, assert outputs are equal.
