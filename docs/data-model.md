# Data Model

> Authoritative entity reference. Every table has these standard columns (omitted from each entity below for brevity):
>
> - `id BINARY(16) PRIMARY KEY` — UUID v7
> - `created_at TIMESTAMP NOT NULL`
> - `created_by BINARY(16)` — FK to `users.id` (nullable for system actions)
> - `updated_at TIMESTAMP NOT NULL`
> - `updated_by BINARY(16)` — FK to `users.id` (nullable)
> - `version BIGINT NOT NULL DEFAULT 0` — optimistic locking
>
> Soft delete is **not** the default. Where retention is required (e.g. `punch_event`), we keep the row and rely on retention policies. Where deletion is fine, we hard-delete and rely on the audit log for forensics.

---

## Module Map

| Module | Tables |
|---|---|
| identity | `user`, `role`, `permission`, `role_permission`, `user_role`, `refresh_token` |
| organization | `employee`, `department`, `user_group`, `employee_group`, `custom_field_definition`, `custom_field_value`, `holiday`, `holiday_group` |
| device | `device`, `ingestion_source`, `device_ingestion_source`, `credential` |
| timecode | `time_code` |
| shift | `shift`, `shift_segment`, `shift_rounding_rule`, `shift_grace_rule`, `break_rule`, `overtime_rule`, `floating_shift_candidate` |
| schedule | `schedule_template`, `schedule_template_day`, `schedule_assignment`, `temporary_schedule` |
| timecard | `punch_event`, `daily_time_card`, `time_card_breakdown`, `time_card_edit` |
| leave | `leave_type`, `leave_balance`, `leave_request` |
| exception | `exception_event` |
| report | `report_job` |
| admin | `audit_event`, `system_setting`, `backup_job`, `retention_policy` |

---

## identity

### `user`
| Column | Type | Notes |
|---|---|---|
| username | VARCHAR(64) UNIQUE NOT NULL | |
| email | VARCHAR(255) UNIQUE NOT NULL | |
| password_hash | VARCHAR(72) NOT NULL | BCrypt |
| status | ENUM | `ACTIVE`, `INACTIVE`, `LOCKED` |
| last_login_at | TIMESTAMP NULL | |
| failed_login_count | INT NOT NULL DEFAULT 0 | |
| locked_until | TIMESTAMP NULL | |

### `role`
| Column | Type | Notes |
|---|---|---|
| name | VARCHAR(64) UNIQUE NOT NULL | |
| description | VARCHAR(255) | |
| is_system | BOOLEAN NOT NULL | system roles cannot be deleted |

Seeded roles: `ADMIN`, `HR_MANAGER`, `MANAGER`, `EMPLOYEE`.

### `permission`
| Column | Type | Notes |
|---|---|---|
| code | VARCHAR(64) UNIQUE NOT NULL | e.g. `schedule.write` |
| description | VARCHAR(255) | |

### `role_permission`
Many-to-many: `role_id`, `permission_id`. Composite PK.

### `user_role`
Many-to-many: `user_id`, `role_id`. Composite PK.

### `refresh_token`
| Column | Type | Notes |
|---|---|---|
| user_id | BINARY(16) NOT NULL | FK user |
| token_hash | CHAR(64) NOT NULL UNIQUE | SHA-256 of the token |
| family_id | BINARY(16) NOT NULL | rotation chain |
| expires_at | TIMESTAMP NOT NULL | |
| revoked_at | TIMESTAMP NULL | set on rotation or logout |
| user_agent | VARCHAR(255) | |
| ip | VARCHAR(45) | IPv6-capable |

Index on `(user_id, revoked_at)`.

---

## organization

### `employee`
| Column | Type | Notes |
|---|---|---|
| user_id | BINARY(16) NULL UNIQUE | FK user (optional — not every employee needs login) |
| employee_code | VARCHAR(64) UNIQUE NOT NULL | external HR ID |
| first_name | VARCHAR(64) NOT NULL | |
| last_name | VARCHAR(64) NOT NULL | |
| email | VARCHAR(255) NULL | |
| phone | VARCHAR(32) NULL | |
| department_id | BINARY(16) NULL | FK department |
| manager_id | BINARY(16) NULL | FK employee (self-ref) |
| employment_type | ENUM | `FULL_TIME`, `PART_TIME`, `CONTRACT`, `TEMP` |
| hire_date | DATE NOT NULL | |
| termination_date | DATE NULL | |
| timezone | VARCHAR(64) NULL | IANA, falls back to dept/org default |
| status | ENUM | `ACTIVE`, `INACTIVE`, `TERMINATED` |

### `department`
| Column | Type | Notes |
|---|---|---|
| name | VARCHAR(128) NOT NULL | |
| parent_id | BINARY(16) NULL | self-ref for hierarchy |
| timezone | VARCHAR(64) NULL | overrides org default |

### `user_group`
Hierarchical grouping orthogonal to departments (used for schedule assignment, manager scope).
| Column | Type | Notes |
|---|---|---|
| name | VARCHAR(128) NOT NULL | |
| parent_id | BINARY(16) NULL | self-ref |
| description | VARCHAR(255) | |

### `employee_group`
Many-to-many: `employee_id`, `group_id`. Composite PK.

### `custom_field_definition`
| Column | Type | Notes |
|---|---|---|
| entity_type | ENUM | `EMPLOYEE` (extensible later) |
| field_key | VARCHAR(64) NOT NULL | machine name |
| display_label | VARCHAR(128) NOT NULL | |
| field_type | ENUM | `STRING`, `NUMBER`, `DATE`, `BOOLEAN`, `ENUM` |
| required | BOOLEAN NOT NULL | |
| options_json | JSON NULL | for ENUM |
| display_order | INT NOT NULL | |

Unique on `(entity_type, field_key)`.

### `custom_field_value`
| Column | Type | Notes |
|---|---|---|
| definition_id | BINARY(16) NOT NULL | FK |
| entity_id | BINARY(16) NOT NULL | polymorphic by definition.entity_type |
| value_string | TEXT NULL | one of these is set per field_type |
| value_number | DECIMAL(20,6) NULL | |
| value_date | DATE NULL | |
| value_boolean | BOOLEAN NULL | |

Unique on `(definition_id, entity_id)`.

### `holiday`
| Column | Type | Notes |
|---|---|---|
| name | VARCHAR(128) NOT NULL | |
| holiday_date | DATE NOT NULL | |
| recurring_yearly | BOOLEAN NOT NULL | if true, year is ignored, MM-DD recurs |
| paid | BOOLEAN NOT NULL | counts as paid time off when scheduled |
| description | VARCHAR(255) | |

### `holiday_group`
Many-to-many scoping: a holiday can apply to all employees or only specific groups.
`holiday_id`, `group_id`. If no rows, applies to all.

---

## device

### `device`
| Column | Type | Notes |
|---|---|---|
| name | VARCHAR(128) NOT NULL | |
| device_type | ENUM | `SIMULATED`, `REST_VIRTUAL`, `EXTERNAL` (reserved for future SDK adapters) |
| location | VARCHAR(255) NULL | |
| status | ENUM | `ACTIVE`, `INACTIVE` |
| capabilities_json | JSON NOT NULL | e.g. `{"check_in":true,"break":true,"face":false}` |
| last_seen_at | TIMESTAMP NULL | |

### `ingestion_source`
Operator-configured source of punch events. The REST adapter is one of these.
| Column | Type | Notes |
|---|---|---|
| name | VARCHAR(128) NOT NULL | |
| source_type | ENUM | `REST`, `DEVICE_SDK`, `EXTERNAL_DB`, `CSV` |
| enabled | BOOLEAN NOT NULL | |
| config_json | JSON NOT NULL | adapter-specific config |
| api_key_hash | CHAR(64) NULL | for REST: SHA-256 of the source's API key |
| last_event_at | TIMESTAMP NULL | |
| events_total | BIGINT NOT NULL DEFAULT 0 | rolling counter |
| events_rejected | BIGINT NOT NULL DEFAULT 0 | |

### `device_ingestion_source`
Optional mapping when one source emits events for multiple logical devices.
`device_id`, `ingestion_source_id`. Composite PK.

### `credential`
| Column | Type | Notes |
|---|---|---|
| employee_id | BINARY(16) NOT NULL | FK employee |
| credential_type | ENUM | `RFID`, `QR`, `MOBILE`, `FACE`, `FINGER`, `PIN` |
| credential_value | VARCHAR(255) NOT NULL | for RFID/QR/PIN: stored hashed; for BIO: reference id |
| credential_lookup | CHAR(64) NOT NULL | SHA-256 of value, indexed for fast resolution |
| valid_from | DATE NOT NULL | |
| valid_to | DATE NULL | |
| status | ENUM | `ACTIVE`, `REVOKED`, `EXPIRED` |

Unique index on `(credential_type, credential_lookup)`.

---

## timecode

### `time_code`
| Column | Type | Notes |
|---|---|---|
| code | VARCHAR(32) UNIQUE NOT NULL | machine name |
| name | VARCHAR(128) NOT NULL | |
| category | ENUM | `ATTENDANCE`, `OVERTIME`, `LEAVE` |
| rate | DECIMAL(4,2) NOT NULL | 0.00–10.00 |
| color | CHAR(7) NOT NULL | `#RRGGBB` |
| paid | BOOLEAN NOT NULL | |
| counts_for_attendance | BOOLEAN NOT NULL | |
| description | VARCHAR(255) | |
| active | BOOLEAN NOT NULL | |

Constraint: `rate BETWEEN 0 AND 10`.

---

## shift

### `shift`
| Column | Type | Notes |
|---|---|---|
| name | VARCHAR(128) NOT NULL | |
| shift_type | ENUM | `FIXED`, `FLEXIBLE`, `FLOATING` |
| color | CHAR(7) NOT NULL | |
| timezone | VARCHAR(64) NULL | overrides employee tz when set |
| description | VARCHAR(255) | |
| active | BOOLEAN NOT NULL | |
| attendance_time_code_id | BINARY(16) NOT NULL | FK time_code (the regular-hours code) |

### `shift_segment`
A shift can have multiple segments (e.g. split shift). Day-relative.
| Column | Type | Notes |
|---|---|---|
| shift_id | BINARY(16) NOT NULL | |
| segment_order | INT NOT NULL | |
| start_minute_of_day | INT NOT NULL | for FIXED: required start; FLEX/FLOAT: window start |
| end_minute_of_day | INT NOT NULL | values > 1440 cross midnight |
| required_minutes | INT NULL | FLEX: required work in this segment |

### `shift_rounding_rule`
| Column | Type | Notes |
|---|---|---|
| shift_id | BINARY(16) NOT NULL | |
| kind | ENUM | `SHIFT`, `PUNCH_IN`, `PUNCH_OUT` |
| unit_minutes | INT NOT NULL | e.g. 15 |
| mode | ENUM | `UP`, `DOWN`, `NEAREST` |

### `shift_grace_rule`
| Column | Type | Notes |
|---|---|---|
| shift_id | BINARY(16) NOT NULL | |
| kind | ENUM | `LATE_IN`, `EARLY_OUT` |
| minutes | INT NOT NULL | |

### `break_rule`
| Column | Type | Notes |
|---|---|---|
| shift_id | BINARY(16) NOT NULL | |
| name | VARCHAR(64) NOT NULL | "Lunch", "Tea", … |
| kind | ENUM | `AUTO_DEDUCT`, `PUNCH_TRACKED` |
| duration_minutes | INT NOT NULL | for AUTO_DEDUCT |
| earliest_start_minute | INT NULL | when to apply |
| after_hours_worked | INT NULL | apply after N minutes worked |
| paid | BOOLEAN NOT NULL | if false, deducted from worked time |
| time_code_id | BINARY(16) NULL | when paid |

### `overtime_rule`
Chronological OT tiers — applied in `sequence_order`.
| Column | Type | Notes |
|---|---|---|
| shift_id | BINARY(16) NOT NULL | |
| sequence_order | INT NOT NULL | |
| after_minutes_worked | INT NOT NULL | tier kicks in after this much work |
| time_code_id | BINARY(16) NOT NULL | FK time_code (must be category OVERTIME) |
| max_minutes | INT NULL | optional cap for this tier |

Unique on `(shift_id, sequence_order)`.

### `floating_shift_candidate`
For FLOATING shifts: which concrete shifts may be auto-selected.
| Column | Type | Notes |
|---|---|---|
| floating_shift_id | BINARY(16) NOT NULL | FK shift (the floating one) |
| candidate_shift_id | BINARY(16) NOT NULL | FK shift (a FIXED/FLEX option) |

Composite PK.

---

## schedule

### `schedule_template`
| Column | Type | Notes |
|---|---|---|
| name | VARCHAR(128) NOT NULL | |
| cycle_type | ENUM | `DAILY`, `WEEKLY` |
| cycle_length_days | INT NOT NULL | 1, 7, 14, 28… |
| description | VARCHAR(255) | |

### `schedule_template_day`
| Column | Type | Notes |
|---|---|---|
| template_id | BINARY(16) NOT NULL | |
| day_index | INT NOT NULL | 0..cycle_length_days-1 |
| shift_id | BINARY(16) NULL | NULL = day off |

Unique on `(template_id, day_index)`.

### `schedule_assignment`
| Column | Type | Notes |
|---|---|---|
| target_type | ENUM | `EMPLOYEE`, `GROUP` |
| target_id | BINARY(16) NOT NULL | FK employee or group |
| template_id | BINARY(16) NOT NULL | |
| start_date | DATE NOT NULL | |
| end_date | DATE NULL | NULL = open-ended |
| priority | INT NOT NULL | resolution tiebreaker, higher wins |

Index on `(target_type, target_id, start_date)`.

### `temporary_schedule`
Per-employee override for a specific date range. Beats any template assignment.
| Column | Type | Notes |
|---|---|---|
| employee_id | BINARY(16) NOT NULL | |
| start_date | DATE NOT NULL | |
| end_date | DATE NOT NULL | |
| shift_id | BINARY(16) NULL | NULL = explicit day-off override |
| reason | VARCHAR(255) | |

Index on `(employee_id, start_date)`.

---

## timecard

### `punch_event`
Raw events as ingested. **Immutable** after persistence — corrections come via `time_card_edit`.
| Column | Type | Notes |
|---|---|---|
| employee_id | BINARY(16) NULL | resolved at ingest; null = unresolved (held in error queue) |
| device_id | BINARY(16) NULL | |
| ingestion_source_id | BINARY(16) NOT NULL | |
| external_event_id | VARCHAR(128) NOT NULL | for idempotency |
| event_type | ENUM | `CHECK_IN`, `CHECK_OUT`, `BREAK_START`, `BREAK_END` |
| event_time_utc | TIMESTAMP NOT NULL | normalized to UTC |
| credential_value_hash | CHAR(64) NULL | what was presented (for auditing unresolved events) |
| raw_payload_json | JSON NULL | original ingest payload, kept for debugging |
| status | ENUM | `PROCESSED`, `UNRESOLVED`, `INVALID`, `SUPERSEDED` |
| processed_at | TIMESTAMP NULL | |

Unique index on `(ingestion_source_id, external_event_id)` — **idempotency**.
Index on `(employee_id, event_time_utc)` for time-card recompute.

### `daily_time_card`
Computed per (employee, work_date). Upserted by the compute engine.
| Column | Type | Notes |
|---|---|---|
| employee_id | BINARY(16) NOT NULL | |
| work_date | DATE NOT NULL | in employee's timezone |
| computed_at | TIMESTAMP NOT NULL | last compute time |
| resolved_shift_id | BINARY(16) NULL | snapshot — may differ from template after floating resolution |
| scheduled_start_utc | TIMESTAMP NULL | |
| scheduled_end_utc | TIMESTAMP NULL | |
| actual_start_utc | TIMESTAMP NULL | first check-in (after rounding) |
| actual_end_utc | TIMESTAMP NULL | last check-out (after rounding) |
| worked_minutes | INT NOT NULL DEFAULT 0 | after breaks |
| break_minutes | INT NOT NULL DEFAULT 0 | |
| overtime_minutes | INT NOT NULL DEFAULT 0 | sum across OT tiers |
| late_minutes | INT NOT NULL DEFAULT 0 | |
| early_out_minutes | INT NOT NULL DEFAULT 0 | |
| status | ENUM | `PRESENT`, `ABSENT`, `LEAVE`, `HOLIDAY`, `OFF`, `PARTIAL` |
| notes | VARCHAR(500) NULL | |

Unique on `(employee_id, work_date)`.

### `time_card_breakdown`
Per-time-code accounting within a day.
| Column | Type | Notes |
|---|---|---|
| daily_time_card_id | BINARY(16) NOT NULL | |
| time_code_id | BINARY(16) NOT NULL | |
| minutes | INT NOT NULL | raw minutes attributed |
| rated_minutes | INT NOT NULL | minutes × rate (rounded) |
| sequence_order | INT NOT NULL | preserves OT tier order for reports |

### `time_card_edit`
Append-only log of manual edits to punches / time cards.
| Column | Type | Notes |
|---|---|---|
| daily_time_card_id | BINARY(16) NOT NULL | |
| punch_event_id | BINARY(16) NULL | when editing a specific punch |
| change_type | ENUM | `PUNCH_ADD`, `PUNCH_EDIT`, `PUNCH_DELETE`, `STATUS_CHANGE`, `NOTE` |
| before_json | JSON NULL | |
| after_json | JSON NULL | |
| reason | VARCHAR(500) NOT NULL | required for audit |
| edited_by_user_id | BINARY(16) NOT NULL | |
| edited_at | TIMESTAMP NOT NULL | |

---

## leave

### `leave_type`
| Column | Type | Notes |
|---|---|---|
| time_code_id | BINARY(16) NOT NULL | must be category LEAVE |
| name | VARCHAR(128) NOT NULL | |
| default_annual_days | DECIMAL(5,2) NOT NULL | starting balance per year |
| requires_approval | BOOLEAN NOT NULL | |
| accrual_rule_json | JSON NULL | future: monthly accrual, carryover, etc. |
| active | BOOLEAN NOT NULL | |

### `leave_balance`
| Column | Type | Notes |
|---|---|---|
| employee_id | BINARY(16) NOT NULL | |
| leave_type_id | BINARY(16) NOT NULL | |
| year | INT NOT NULL | |
| balance_days | DECIMAL(5,2) NOT NULL | |

Unique on `(employee_id, leave_type_id, year)`.

### `leave_request`
| Column | Type | Notes |
|---|---|---|
| employee_id | BINARY(16) NOT NULL | |
| leave_type_id | BINARY(16) NOT NULL | |
| start_date | DATE NOT NULL | |
| end_date | DATE NOT NULL | |
| half_day | BOOLEAN NOT NULL DEFAULT FALSE | |
| half_day_part | ENUM NULL | `FIRST_HALF`, `SECOND_HALF` |
| reason | VARCHAR(500) | |
| status | ENUM | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| retroactive | BOOLEAN NOT NULL DEFAULT FALSE | |
| approved_by | BINARY(16) NULL | FK user |
| approved_at | TIMESTAMP NULL | |
| rejection_reason | VARCHAR(500) | |

Index on `(employee_id, start_date)`.

---

## exception

### `exception_event`
| Column | Type | Notes |
|---|---|---|
| employee_id | BINARY(16) NOT NULL | |
| work_date | DATE NOT NULL | |
| daily_time_card_id | BINARY(16) NULL | FK time card |
| exception_type | ENUM | `MISSING_PUNCH_IN`, `MISSING_PUNCH_OUT`, `LATE_IN`, `EARLY_OUT`, `ABSENT_NO_LEAVE`, `UNAUTHORIZED_OT`, `ORPHAN_PUNCH` |
| severity | ENUM | `INFO`, `WARN`, `CRITICAL` |
| details_json | JSON NULL | type-specific detail |
| status | ENUM | `OPEN`, `RESOLVED`, `IGNORED` |
| resolved_by | BINARY(16) NULL | FK user |
| resolved_at | TIMESTAMP NULL | |
| resolution_note | VARCHAR(500) NULL | |

Index on `(employee_id, work_date)`, `(status, severity)`.

---

## report

### `report_job`
| Column | Type | Notes |
|---|---|---|
| report_type | ENUM | `DAILY`, `DAILY_SUMMARY`, `INDIVIDUAL`, `INDIVIDUAL_SUMMARY`, `LEAVE`, `EXCEPTION`, `MODIFIED_PUNCH_LOG` |
| parameters_json | TEXT NOT NULL | date range, filters, sort, custom fields (JSON as TEXT per ADR 0003) |
| requested_by | BINARY(16) NULL | requesting user; null for system-initiated runs |
| status | ENUM | `QUEUED`, `RUNNING`, `DONE`, `FAILED`, `CANCELLED` |
| file_path | VARCHAR(500) NULL | location of CSV output |
| row_count | BIGINT NULL | |
| started_at | TIMESTAMP NULL | |
| completed_at | TIMESTAMP NULL | |
| error_message | VARCHAR(1000) NULL | |

---

## admin

### `audit_event`
Append-only. No updates, no deletes (except via retention purge).
| Column | Type | Notes |
|---|---|---|
| actor_user_id | BINARY(16) NULL | nullable for system actions |
| actor_username | VARCHAR(64) NOT NULL | denormalized for forensics |
| action | VARCHAR(64) NOT NULL | `CREATE`, `UPDATE`, `DELETE`, `LOGIN`, `LOGOUT`, `EXPORT`, … |
| entity_type | VARCHAR(64) NULL | |
| entity_id | BINARY(16) NULL | |
| before_json | TEXT NULL | JSON as TEXT per ADR 0003 |
| after_json | TEXT NULL | JSON as TEXT per ADR 0003 |
| ip | VARCHAR(45) NULL | |
| user_agent | VARCHAR(255) NULL | |
| request_id | CHAR(36) NULL | trace correlation |
| occurred_at | TIMESTAMP NOT NULL | |

Indexes: `(occurred_at)`, `(actor_user_id, occurred_at)`, `(entity_type, entity_id)`.
Retention purge is the only delete path (via the `audit_event` `RetentionPort`).

### `system_setting`
Key-value store for org-wide settings (audited config; standard `id`/audit/version columns).
| Column | Type | Notes |
|---|---|---|
| setting_key | VARCHAR(64) UNIQUE NOT NULL | natural key (the standard `id` is the PK) |
| setting_value | TEXT NOT NULL | |
| value_type | ENUM | `STRING`, `NUMBER`, `BOOLEAN`, `JSON` |
| description | VARCHAR(255) | |

Updates are type-validated (`NUMBER` numeric, `BOOLEAN` true/false, `JSON` parseable, and
`*_cron` keys must be valid Spring cron). Seeded keys (V11): `org_name`, `default_timezone`,
`week_start_day`, `password_min_length`, `backup_enabled`, `backup_cron`, `backup_keep_count`,
`retention_enabled`, `retention_cron`. The `*_cron` keys drive the dynamic backup / retention
schedulers, so editing them re-schedules without a restart.

### `backup_job`
Job-tracking table (not audited), like `report_job`.
| Column | Type | Notes |
|---|---|---|
| trigger_type | ENUM | `SCHEDULED`, `MANUAL` (`trigger` is reserved SQL) |
| status | ENUM | `RUNNING`, `DONE`, `FAILED` |
| file_path | VARCHAR(500) NULL | dump written under `backups/` |
| size_bytes | BIGINT NULL | |
| started_at | TIMESTAMP NOT NULL | |
| completed_at | TIMESTAMP NULL | |
| error_message | VARCHAR(1000) NULL | |

The dump command comes from the `DatabaseDialectPort` (MariaDB → `mysqldump`); a successful run
triggers keep-count rotation (`backup_keep_count`).

### `retention_policy`
Audited config; standard `id`/audit/version columns.
| Column | Type | Notes |
|---|---|---|
| entity_type | VARCHAR(64) UNIQUE NOT NULL | matches a module's `RetentionPort` (`punch_event`, `audit_event`, `report_job`) |
| retain_days | INT NOT NULL | |
| enabled | BOOLEAN NOT NULL | |
| last_run_at | TIMESTAMP NULL | |
| last_run_deleted | BIGINT NULL | rows deleted on the last run |

Seeded disabled (V11): `punch_event` 365d, `audit_event` 730d, `report_job` 90d. Purge runs in
bounded batches via the owning module's `RetentionPort`; `punch_event` keeps any punch referenced
by a manual edit.

---

## Relationships (high level)

```
user ─┐
      ├─< user_role >─ role ─< role_permission >─ permission
      └─ refresh_token

employee ─< credential
employee ─< employee_group >─ user_group ─(self)
employee >─ department ─(self)
employee >─ manager (employee, self-ref)

shift ─< shift_segment
      ─< shift_rounding_rule
      ─< shift_grace_rule
      ─< break_rule
      ─< overtime_rule >─ time_code
      ─< floating_shift_candidate

schedule_template ─< schedule_template_day >─ shift
schedule_assignment >─ schedule_template
                   >─ (employee | user_group)
temporary_schedule >─ employee, shift

ingestion_source ─< punch_event
device           ─< punch_event (optional)
employee         ─< punch_event ─(idempotency)─ external_event_id

daily_time_card  >─ employee, resolved_shift
                 ─< time_card_breakdown >─ time_code
                 ─< time_card_edit >─ user (editor)
                 ─< exception_event

leave_type >─ time_code
leave_balance >─ employee, leave_type
leave_request >─ employee, leave_type, approver(user)

audit_event >─ user (actor)
```

## Indexes Cheat Sheet

| Table | Index | Purpose |
|---|---|---|
| `user` | `username`, `email` | login lookup |
| `refresh_token` | `(user_id, revoked_at)` | active token lookup |
| `employee` | `employee_code`, `department_id`, `status` | lookups, filters |
| `credential` | `(credential_type, credential_lookup)` UNIQUE | ingest resolution |
| `punch_event` | `(ingestion_source_id, external_event_id)` UNIQUE | idempotency |
| `punch_event` | `(employee_id, event_time_utc)` | recompute scan |
| `daily_time_card` | `(employee_id, work_date)` UNIQUE | upsert key |
| `schedule_assignment` | `(target_type, target_id, start_date)` | schedule resolution |
| `temporary_schedule` | `(employee_id, start_date)` | override lookup |
| `audit_event` | `(occurred_at)`, `(entity_type, entity_id)` | timeline + entity history |
| `exception_event` | `(status, severity)`, `(employee_id, work_date)` | queue + history |
