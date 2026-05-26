# Architecture Decision Records

Each ADR documents one significant architectural decision: its context, the choice, and the trade-offs.

## Format

ADRs follow the lightweight template used by Michael Nygard:
- **Status** — Proposed / Accepted / Superseded by ADR-NNNN
- **Context** — what forces are at play
- **Decision** — what we're doing
- **Consequences** — positive, negative, and what we're explicitly rejecting

## Index

| # | Title | Status |
|---|---|---|
| [0001](0001-modular-monolith.md) | Modular Monolith with Hexagonal Seams | Accepted |
| [0002](0002-jwt-stateless-auth.md) | Stateless JWT Authentication | Accepted |
| [0003](0003-mariadb-with-dialect-abstraction.md) | MariaDB Default with Dialect Abstraction | Accepted |
| [0004](0004-pluggable-ingestion.md) | Pluggable Punch Event Ingestion | Accepted |
| [0005](0005-uuid-v7-primary-keys.md) | UUID v7 Primary Keys | Accepted |
| [0006](0006-mantine-ui.md) | Mantine for Frontend Components | Accepted |
| [0007](0007-time-card-pure-computation.md) | Time Card Computation Is a Pure Function | Accepted |

## Adding a new ADR

1. Copy the most recent file as a template.
2. Number it sequentially.
3. Set status to `Proposed`.
4. Open a PR for discussion.
5. On merge, change status to `Accepted`. If it supersedes an earlier ADR, mark the old one `Superseded by ADR-NNNN` in the same PR.
