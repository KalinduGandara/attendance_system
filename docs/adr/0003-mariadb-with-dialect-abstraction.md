# ADR-0003: MariaDB Default with Dialect Abstraction

**Status:** Accepted
**Date:** 2026-05-26

## Context
BioStar 2 standardizes on MariaDB. To minimize friction for users coming from that ecosystem, we should default to MariaDB. But we also want the option to deploy on PostgreSQL or MSSQL without rewriting the data layer.

## Decision
- **Primary database**: MariaDB 10.11+.
- **Persistence**: JPA / Hibernate with **dialect-neutral schema and queries**.
- **Migrations**: Flyway, with all migrations written in **portable ANSI SQL**. Vendor-specific overrides live in `db/migration/{vendor}/` and are layered conditionally.
- **Vendor-specific behavior** (locking hints, JSON operators, full-text search, current-timestamp expressions) is encapsulated behind a `DatabaseDialectPort` interface. The default implementation returns ANSI-compliant SQL; vendor adapters override only what they need.
- **Native queries** are forbidden outside the dialect port. JPA repository methods, derived queries, and JPQL are fine.
- **No reliance on vendor-specific features** in v1 (e.g. no `JSON_TABLE`, no MariaDB sequences, no MSSQL TOP).

## Consequences

**Positive**
- Out-of-the-box MariaDB experience matches BioStar 2 expectations.
- PostgreSQL and MSSQL are achievable later by writing one adapter class plus the `db/migration/{vendor}/` overrides.
- The "no native queries outside the port" rule keeps the door open without constant policing.

**Negative**
- We lose some MariaDB-specific performance levers (e.g. virtual columns, full-text). If we ever need them, the dialect port has a place to add them without forking the codebase.
- ANSI SQL can be more verbose for things like upserts. Tolerable.

**Verification**
- A CI lint job greps `**/*.java` and `**/*.sql` for known vendor functions (`FROM_UNIXTIME`, `JSON_EXTRACT`, `DATEADD`, `TOP`, …) and fails outside the dialect port's allow-list.
