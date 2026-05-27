# Attendance System

A web-based Time & Attendance platform inspired by BioStar 2 T&A. Single-tenant, self-hosted, with a pluggable ingestion layer so punch events can come from REST, future device SDKs, external databases, or file imports.

## Tech Stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Maven |
| Persistence | JPA / Hibernate, Flyway, MariaDB (extensible to PostgreSQL / MSSQL) |
| Security | Spring Security + JWT (access + refresh) |
| Frontend | React 18, TypeScript, Vite, Mantine, TanStack Query, React Router |
| Tests | JUnit 5 + Testcontainers (backend), Vitest + Playwright (frontend) |
| Deployment | Docker Compose |

## Repository Layout

```
attendance_system2/
├── backend/            Spring Boot application (Maven)
├── frontend/           React + Vite application
├── docs/               Architecture, plan, ADRs, data model, API, runbook
├── docker-compose.yml  Local dev stack (app + MariaDB)
└── srs.md              Source SRS document
```

## Documentation Map

Read in this order:

1. [docs/architecture.md](docs/architecture.md) — system design, modules, key abstractions
2. [docs/data-model.md](docs/data-model.md) — entities and relationships
3. [docs/api-contracts.md](docs/api-contracts.md) — REST API surface
4. [docs/plan.md](docs/plan.md) — phased implementation plan
5. [docs/runbook.md](docs/runbook.md) — local setup, backups, restore
6. [docs/adr/](docs/adr/) — Architecture Decision Records

## Quick start (dev)

```
cp .env.example .env
docker compose up --build       # mariadb :3306, backend :3000, frontend :8080
```

Bootstrap credentials (rotate immediately):

| username | password      |
|----------|---------------|
| `admin`  | `Admin@12345` |

Browse:
- Frontend: http://localhost:8080
- Backend API: http://localhost:3000/api/v1/health
- Swagger UI: http://localhost:3000/swagger-ui

Without Docker:

```
# backend
cd backend && mvn spring-boot:run         # needs MariaDB on :3306

# frontend
cd frontend && npm install && npm run dev # http://localhost:5173, proxies /api to :3000
```

## Status

**Phase 1 (Identity & Organization) complete.** Adds:

- Tables (V3 + V4 migrations): `department`, `user_group`, `employee`, `employee_group`,
  `custom_field_definition`, `custom_field_value`, `holiday`, `holiday_group`,
  `employee_import_job`.
- REST CRUD for users, roles (read), permissions (read), employees, departments (with tree),
  groups (with tree), custom fields, holidays. All write endpoints permission-gated and audited.
- Manager-scope service: given a user, returns the set of visible employee ids (admin/HR sees all;
  managers see self + transitive direct reports). Foundation for Phase 4+ row-level filters.
- Bulk employee CSV import — synchronous below 1000 rows, async (`@Async`) above. Job status
  persisted in `employee_import_job`.
- Frontend pages (React Hook Form + Zod, permission-gated): Users, Employees list & form
  (with custom-field inputs), Departments tree, Groups tree, Custom Fields, Holidays.
- Tests (28 passing): department / group hierarchy + cycle detection, employee CRUD + custom
  fields, manager scope resolution, CSV import (sync + error reporting), controller security.

**Phase 0 (Foundation) complete.** End-to-end auth slice, Flyway-managed schema, audit
infrastructure, OpenAPI/Swagger, Mantine app shell, dev docker-compose, GitHub Actions CI.

Next: Phase 2 — Devices, Credentials, Ingestion Sources (see [docs/plan.md](docs/plan.md)).
