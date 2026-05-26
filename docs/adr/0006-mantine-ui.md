# ADR-0006: Mantine for Frontend Components

**Status:** Accepted
**Date:** 2026-05-26

## Context
The frontend is a data-heavy admin SPA: tables, forms, calendars, date pickers, modals, toasts. Building this from scratch would burn weeks before we ship feature value. The choice is between mature component libraries.

## Decision
Use **Mantine** as the primary component library.

- Use `@mantine/core`, `@mantine/hooks`, `@mantine/dates`, `@mantine/notifications`, `@mantine/modals`, `@mantine/form` (we'll prefer React Hook Form for complex forms but Mantine Form is fine for simple ones).
- For the schedule/timecard calendar views, we'll use **FullCalendar** instead of Mantine's calendar — Mantine's is a date picker, not an event calendar.

## Consequences

**Positive**
- Excellent TypeScript support (everything typed, no `any` escape hatches).
- Batteries-included: date pickers, forms, notifications, modals all in one cohesive API.
- Theming is straightforward; Mantine's default tokens are clean and we can extend without fighting the library.
- Good a11y baseline (ARIA, keyboard nav).
- Smaller bundle than MUI, less prescriptive than Ant Design.

**Negative**
- Smaller community than MUI; rarer Stack Overflow questions for edge cases.
- Some enterprise components (heavy data grids) are weaker — we'll evaluate `mantine-react-table` vs `@tanstack/react-table` per feature.

**Rejected alternatives**
- MUI: solid choice, but heavier and more prescriptive on styling. Would constrain our look-and-feel customizations more than we'd like.
- Ant Design: strong tables/forms but a distinctive visual style that's hard to neutralize without a lot of theming work.
- Headless UI (Radix + custom styling): maximum control but multiplies UI work — wrong tradeoff for an admin product where we want to ship features.
