# Performance harness

Phase 10 (§10 of [plan.md](../docs/plan.md)) sets two performance targets:

| Target | Where it's verified |
|---|---|
| Ingestion sustains **500 events/sec** | `load-test.js` (k6) against a running stack |
| Time-card recompute **p95 < 100 ms** per (employee, day) | `timecard_recompute_seconds` in Prometheus, plus the in-process guard `RecomputeBenchmarkTest` (pure engine) in CI |

## 1. Recompute SLO — the fast guard (no infra)

The pure computation core is exercised on every backend build:

```
cd backend && ./mvnw test -Dtest=RecomputeBenchmarkTest
```

It runs `TimeCardCalculator.compute` 20k times on a loaded fixture and asserts
the p95 stays far inside budget (prints `p50/p95/p99`). This catches algorithmic
regressions in the engine without needing a database. The end-to-end SLO
(including DB round-trips) is measured live, below.

## 2. Ingestion load test (500 ev/s)

Prerequisites:
- A running stack (`docker compose up`).
- [k6](https://k6.io/docs/get-started/installation/) installed.
- An ingestion source + its plaintext API key (Ingestion Sources page → *Rotate
  key*; the key is shown once).
- A handful of employee UUIDs to spread punches across (so recompute runs).

```
k6 run \
  -e BASE_URL=http://localhost:3000 \
  -e SOURCE_ID=<ingestion-source-uuid> \
  -e API_KEY=<plaintext-api-key> \
  -e EMPLOYEE_IDS=<uuid1,uuid2,uuid3> \
  -e RATE=20 -e BATCH=25 -e DURATION=2m \
  load-test.js
```

`RATE * BATCH` is the events/sec. The defaults (20 req/s × 25) = **500 ev/s**.
Thresholds fail the run if the ingestion API p95 exceeds 500 ms or the error
rate exceeds 1%.

### Watching recompute during the run

Recompute is asynchronous (fired by `PunchEventIngested`), so it is **not** in
the k6 HTTP timings. Read it from the metrics endpoint while the test runs:

```
curl -s localhost:3000/actuator/prometheus | grep timecard_recompute_seconds
```

Look at `timecard_recompute_seconds{quantile="0.95"}` — it must be `< 0.1`.
`ingestion_punches_total{status="accepted"}` should climb at ~500/s.

## 3. Recording results

Capture the k6 summary and the relevant Prometheus gauges into a dated entry
below so regressions are visible across releases.

| Date | Env | Ingestion p95 | Sustained ev/s | Recompute p95 | Notes |
|---|---|---|---|---|---|
| _pending first prod-like run_ | | | | | recorded after a staging run |

> The pure-engine guard (`RecomputeBenchmarkTest`) runs in CI and is the
> regression tripwire; the table above is filled from a manual staging run on
> representative hardware, since this repo's dev environment is not a load-test
> target.
