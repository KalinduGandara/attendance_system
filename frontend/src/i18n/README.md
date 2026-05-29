# Internationalization (i18n)

Phase 10 introduced i18n **scaffolding**: the infrastructure plus a complete
English reference bundle. The goal is that **every user-facing string resolves
through `t()`**, so adding a locale is just a new resource file — no component
edits.

## How it works

- [`index.ts`](./index.ts) initializes [`i18next`](https://www.i18next.com/) +
  [`react-i18next`](https://react.i18next.com/) with `en` as both the active and
  fallback language. It is imported once in [`../main.tsx`](../main.tsx) before
  the React tree renders.
- [`en.json`](./en.json) is the single source of truth for English copy,
  organized by area: `common`, `nav`, `auth`, `errors`, `dashboard`,
  `timecard`, `leave`, …

## Using it in a component

```tsx
import { useTranslation } from 'react-i18next';

export function Example() {
  const { t } = useTranslation();
  return <Button>{t('common.actions.save')}</Button>;
}
```

Interpolation and pluralization:

```tsx
t('dashboard.welcome', { name: user.displayName }); // "Welcome, Ada."
t('dashboard.permissionsGranted', { count: 3 });     // "3 granted"
```

## Conventions

1. **No bare strings in JSX.** A literal that the user can read belongs in
   `en.json` and is referenced by key. (Exceptions: `data-testid`, icon names,
   and developer-only console text.)
2. **Key by area, then by meaning** — `feature.section.thing`, not by English
   wording. `timecard.view.calendar`, not `timecard.calendarButton`.
3. **Reuse `common.*`** for shared verbs/labels (Save, Cancel, Retry…).
4. **Add the key to `en.json` first**, then reference it. The English bundle
   must stay complete; missing keys fall back to the raw key string.

## Migration status

Fully migrated (cross-cutting + critical flow): the app shell & navigation,
login, the 403/404 pages, the dashboard, and the time-card dashboard.

Remaining feature pages (identity, organization, device, shift, schedule,
report, exception, full leave, admin) still contain inline English and are
migrated incrementally against the conventions above. New code must use `t()`.
