# API Contracts

> The full source of truth is the live OpenAPI spec served at `GET /api/openapi.json` and the Swagger UI at `GET /swagger-ui`. This document describes the **shape** of the API: conventions, base contracts, and a representative endpoint per module. The frontend's TypeScript client is generated from the live spec.

## 1. Conventions

### Base URL
`/api/v1`

### Headers
| Header | Purpose |
|---|---|
| `Authorization: Bearer <jwt>` | Required for all non-public endpoints |
| `Content-Type: application/json` | Default |
| `X-Request-Id` | Optional; echoed back; used in logs and audit |
| `Idempotency-Key` | Required on `POST /ingestion/punches` |

### Response envelopes
- **Success**: bare resource (single object) or paged collection
- **Error**: RFC 7807 problem-details

### Paged collection format
```json
{
  "items": [...],
  "page": 0,
  "size": 50,
  "totalElements": 1234,
  "totalPages": 25,
  "sort": "lastName,asc"
}
```

### Error format (RFC 7807)
```json
{
  "type": "https://attendance.local/errors/validation",
  "title": "Validation failed",
  "status": 400,
  "detail": "Body has invalid fields",
  "instance": "/api/v1/employees",
  "requestId": "8b5c…",
  "errors": [
    { "field": "email", "code": "FORMAT", "message": "must be a valid email" }
  ]
}
```

### Pagination & sorting
- Query params: `page` (0-indexed), `size` (max 200), `sort` (`field,asc|desc`, repeatable)

### IDs
- All resource IDs are UUIDs in canonical hyphenated form, e.g. `0193f7c5-7d2a-7c2b-9b3a-f8c52d3e0b91`.

### Date / time
- All timestamps are ISO 8601 in UTC: `2026-05-26T14:23:00Z`
- All dates are `YYYY-MM-DD`

### HTTP status codes
| Code | Meaning |
|---|---|
| 200 | OK |
| 201 | Created (response includes the resource) |
| 204 | No Content (deletes, some updates) |
| 400 | Validation error |
| 401 | Missing / invalid auth |
| 403 | Authenticated but not authorized |
| 404 | Not found |
| 409 | Conflict (optimistic lock, duplicate, business rule violation) |
| 422 | Domain rule violation (e.g. overtime exceeds cap) |
| 429 | Rate limited |
| 500 | Unexpected server error |

## 2. Authentication

### `POST /auth/login`
Public. Issues access token + refresh cookie.

Request:
```json
{ "username": "alice", "password": "..." }
```
Response 200:
```json
{
  "accessToken": "eyJhbGciOi…",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "0193…",
    "username": "alice",
    "displayName": "Alice Doe",
    "roles": ["MANAGER"],
    "permissions": ["schedule.write", "timecard.read", "..."]
  }
}
```
Sets cookie `refreshToken=<opaque>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth`.

### `POST /auth/refresh`
Reads refresh cookie, issues new access token, rotates refresh cookie.

### `POST /auth/logout`
Revokes the current refresh token family.

### `GET /auth/me`
Returns the current user (same shape as `login.user`).

### `POST /auth/change-password`
Body: `{ "currentPassword": "...", "newPassword": "..." }`

## 3. Module Endpoints (summary)

Each row links the module to its primary endpoint group. Full details live in the OpenAPI spec.

| Module | Base path | CRUD |
|---|---|---|
| Identity (users) | `/users` | full |
| Identity (roles) | `/roles` | read + assign |
| Identity (permissions) | `/permissions` | read-only seeded set |
| Organization (employees) | `/employees` | full + bulk import |
| Organization (departments) | `/departments` | full |
| Organization (groups) | `/groups` | full |
| Organization (custom fields) | `/custom-fields` | full |
| Organization (holidays) | `/holidays` | full |
| Device | `/devices` | full |
| Ingestion sources | `/ingestion-sources` | full |
| Ingestion punches | `/ingestion/punches` | write-only |
| Credentials | `/employees/{id}/credentials` | full |
| Time codes | `/time-codes` | full |
| Shifts | `/shifts` | full (with nested rules) |
| Schedule templates | `/schedule-templates` | full |
| Schedule assignments | `/schedule-assignments` | full |
| Temporary schedules | `/temporary-schedules` | full |
| Punch events | `/punch-events` | read + idempotent write via `/ingestion/punches` |
| Daily time cards | `/timecards` | read + manual edit |
| Leave types | `/leave-types` | full |
| Leave balances | `/leave-balances` | read + adjust |
| Leave requests | `/leave-requests` | full + approve/reject |
| Exceptions | `/exceptions` | read + resolve |
| Reports | `/reports` | run + download |
| Audit log | `/audit-events` | read-only |
| System settings | `/system/settings` | read + update |
| Backups | `/system/backups` | run + read |
| Retention policies | `/system/retention-policies` | read + update |

## 4. Representative Endpoints

### 4.1 Ingestion — `POST /ingestion/punches`
The single most important external endpoint. Accepts a batch.

Headers:
- `Authorization: Bearer <jwt>` *or* `X-Source-Api-Key: <key>` (ingestion source API key)
- `Idempotency-Key: <uuid>` — covers the whole batch request

Request:
```json
{
  "sourceId": "0193f7c5-...",
  "events": [
    {
      "externalEventId": "device-7:2026-05-26T14:23:00Z",
      "eventType": "CHECK_IN",
      "eventTime": "2026-05-26T14:23:00Z",
      "credential": { "type": "RFID", "value": "0x4F2A1C…" },
      "deviceId": "0193…",
      "rawPayload": { "...": "..." }
    }
  ]
}
```
- `externalEventId` MUST be stable for re-submission.
- `credential.value` is hashed server-side before lookup.

Response 200:
```json
{
  "accepted": 1,
  "duplicate": 0,
  "unresolved": 0,
  "invalid": 0,
  "results": [
    {
      "externalEventId": "device-7:2026-05-26T14:23:00Z",
      "status": "ACCEPTED",
      "punchEventId": "0193f7c6-..."
    }
  ]
}
```

Possible `status` values: `ACCEPTED`, `DUPLICATE`, `UNRESOLVED_CREDENTIAL`, `INVALID`.

### 4.2 Time card — `GET /timecards`
List daily time cards.

Query: `employeeId`, `from`, `to`, `status`, `groupId`, plus pagination.

Item:
```json
{
  "id": "0193…",
  "employee": { "id": "0193…", "code": "E1023", "name": "Alice Doe" },
  "workDate": "2026-05-26",
  "status": "PRESENT",
  "resolvedShift": { "id": "0193…", "name": "Day 9-5", "color": "#3b82f6" },
  "scheduledStart": "2026-05-26T09:00:00Z",
  "scheduledEnd": "2026-05-26T17:00:00Z",
  "actualStart": "2026-05-26T09:02:00Z",
  "actualEnd": "2026-05-26T17:15:00Z",
  "workedMinutes": 480,
  "breakMinutes": 60,
  "overtimeMinutes": 15,
  "lateMinutes": 2,
  "earlyOutMinutes": 0,
  "breakdown": [
    { "timeCode": "REG", "minutes": 480, "ratedMinutes": 480 },
    { "timeCode": "OT-A", "minutes": 15, "ratedMinutes": 23 }
  ],
  "exceptions": [
    { "type": "LATE_IN", "severity": "INFO" }
  ]
}
```

### 4.3 Time card edit — `POST /timecards/{id}/edits`
Manual edit by an authorized manager. Always requires a reason.

Request:
```json
{
  "changeType": "PUNCH_EDIT",
  "punchEventId": "0193…",
  "newEventTime": "2026-05-26T09:00:00Z",
  "reason": "Device clock drift; verified by team lead"
}
```

Side effects:
- A new immutable `punch_event` row is created (the original is marked `SUPERSEDED`).
- A `time_card_edit` row is appended.
- An `audit_event` row is appended.
- The time card is recomputed synchronously and returned.

Response 200: the updated `TimeCard`.

### 4.4 Schedule — `POST /schedule-assignments`
Assign a template to an employee or group over a date range.

Request:
```json
{
  "targetType": "GROUP",
  "targetId": "0193…",
  "templateId": "0193…",
  "startDate": "2026-06-01",
  "endDate": "2026-12-31",
  "priority": 10
}
```
On success: 201 + the created assignment + queues a bounded recompute for the affected window.

### 4.5 Reports — `POST /reports`
Kicks off an async report job. All report endpoints require `report.run`.

`reportType` is one of `DAILY`, `DAILY_SUMMARY`, `INDIVIDUAL`, `INDIVIDUAL_SUMMARY`,
`LEAVE`, `EXCEPTION`, `MODIFIED_PUNCH_LOG`. `parameters.from`/`to` are required; `INDIVIDUAL`
also requires `employeeId`. `status` filters the Leave / Exception reports. Scope narrows by
`employeeId` → `groupId` → `departmentId`, defaulting to all employees. `includeCustomFields`
appends employee custom-field columns; `sort` applies best-effort lexical overrides.

Request:
```json
{
  "reportType": "DAILY_SUMMARY",
  "parameters": {
    "from": "2026-05-01",
    "to": "2026-05-31",
    "groupId": "0193…",
    "includeCustomFields": ["cost_center", "shift_lead"],
    "sort": [{ "field": "employee.lastName", "dir": "asc" }]
  }
}
```
Response 202:
```json
{ "id": "0193…", "reportType": "DAILY_SUMMARY", "status": "QUEUED", "downloadUrl": null }
```

### `GET /reports`
Lists the caller's most recent report jobs (newest first).

### `GET /reports/{id}`
Returns status + `downloadUrl` (non-null only when `DONE`) + `rowCount` / `errorMessage`.

### `GET /reports/{id}/download`
Streams the generated CSV (`text/csv`, `Content-Disposition: attachment`). 409 if the job is
not `DONE`; 404 if the file is missing.

### 4.6 Audit log — `GET /audit-events`
Filterable view of the audit trail.

Query: `actorUserId`, `entityType`, `entityId`, `action`, `from`, `to`, plus pagination, sorted by `occurredAt,desc` by default.

### 4.7 System settings — `GET /system/settings`
Returns all settings.

### `PATCH /system/settings`
Body is a partial object of keys to update. Every write is audited.

## 5. WebSocket / SSE (future)

For phase 6+: live-monitoring of punches and time-card updates.
- `GET /api/v1/stream/timecards` (SSE) — server-sent events of time-card recompute results, scoped by the connected user's permission.
- Authentication via short-lived access token in the URL or via the access cookie used by `/auth/refresh` (we'll pick when we implement).

## 6. Security on the API surface

- All endpoints (except `/auth/login`, `/auth/refresh`, `/health`) require `Authorization: Bearer`.
- Per-endpoint authorization is annotation-driven on the controller methods; the OpenAPI spec exposes the required permission codes.
- Row-level scope (managers see only their group's data) is enforced inside service queries — controllers do **not** trust client-supplied scope.
- Rate limits:
  - `/auth/login`: 5 / 15 min per IP+username
  - `/ingestion/punches`: 1000 / min per source API key
  - All other endpoints: 600 / min per user (configurable)

## 7. Versioning

- `/api/v1` is the only public version through v1 of the product.
- Breaking changes will introduce `/api/v2` alongside, with a deprecation window communicated in release notes.
- Additive changes (new fields, new endpoints) happen within `v1` and do not bump the version.
