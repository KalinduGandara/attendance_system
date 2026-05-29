/*
 * Phase 10 hardening — ingestion load test.
 *
 * plan.md §10 target: sustain 500 punch events/sec. This k6 script drives the
 * batch ingestion endpoint at a constant arrival rate using the API-key auth
 * path (the realistic device/integration path). Throughput = RATE * BATCH.
 *
 * Run (needs a running stack + a provisioned ingestion source API key):
 *
 *   k6 run \
 *     -e BASE_URL=http://localhost:3000 \
 *     -e SOURCE_ID=<ingestion source uuid> \
 *     -e API_KEY=<plaintext key from the Ingestion Sources "rotate key" dialog> \
 *     -e EMPLOYEE_IDS=<uuid,uuid,...> \
 *     -e RATE=20 -e BATCH=25 -e DURATION=2m \
 *     perf/load-test.js
 *
 * Defaults give 20 req/s * 25 events = 500 events/sec for 2 minutes.
 *
 * The recompute SLO (p95 < 100 ms per employee/day) is asynchronous and is NOT
 * measured here — read it from Prometheus while this runs (see perf/README.md):
 *   timecard_recompute_seconds{quantile="0.95"}
 */
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:3000';
const SOURCE_ID = __ENV.SOURCE_ID;
const API_KEY = __ENV.API_KEY;
const EMPLOYEE_IDS = (__ENV.EMPLOYEE_IDS || '').split(',').filter(Boolean);
const RATE = parseInt(__ENV.RATE || '20', 10);
const BATCH = parseInt(__ENV.BATCH || '25', 10);
const DURATION = __ENV.DURATION || '2m';

const acceptedEvents = new Counter('punch_events_accepted');

export const options = {
  scenarios: {
    ingest: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: Math.max(20, RATE * 2),
      maxVUs: Math.max(50, RATE * 5)
    }
  },
  thresholds: {
    // Ingestion API latency (persistence + idempotency check), not recompute.
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.01']
  }
};

export function setup() {
  if (!SOURCE_ID || !API_KEY) {
    throw new Error('SOURCE_ID and API_KEY are required. See the header of this file.');
  }
  if (EMPLOYEE_IDS.length === 0) {
    // Events still persist (UNRESOLVED) and exercise ingestion throughput, but
    // no recompute is triggered. Warn so the operator knows what they measured.
    console.warn('EMPLOYEE_IDS empty — events will persist as UNRESOLVED; recompute is not exercised.');
  }
}

function buildBatch() {
  const now = Date.now();
  const events = [];
  for (let i = 0; i < BATCH; i++) {
    const employeeId =
      EMPLOYEE_IDS.length > 0
        ? EMPLOYEE_IDS[(__VU + __ITER + i) % EMPLOYEE_IDS.length]
        : undefined;
    events.push({
      // Globally unique so nothing dedups away under load.
      externalEventId: `k6-${__VU}-${__ITER}-${i}-${now}`,
      eventType: i % 2 === 0 ? 'CHECK_IN' : 'CHECK_OUT',
      eventTime: new Date(now - i * 1000).toISOString(),
      employeeId
    });
  }
  return { sourceId: SOURCE_ID, events };
}

export default function () {
  const payload = buildBatch();
  const res = http.post(`${BASE_URL}/api/v1/ingestion/punches`, JSON.stringify(payload), {
    headers: {
      'Content-Type': 'application/json',
      'X-Source-Api-Key': API_KEY,
      // Whole-batch idempotency key; unique per request here.
      'Idempotency-Key': `k6-${__VU}-${__ITER}-${Date.now()}`
    }
  });

  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'body has counts': (r) => {
      try {
        return typeof JSON.parse(r.body).accepted === 'number';
      } catch (_e) {
        return false;
      }
    }
  });

  if (ok && res.status === 200) {
    try {
      acceptedEvents.add(JSON.parse(res.body).accepted);
    } catch (_e) {
      // ignore parse failures already flagged by the check above
    }
  }
}
