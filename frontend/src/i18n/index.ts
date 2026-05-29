import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './en.json';

/**
 * i18n scaffolding (Phase 10). Every user-facing string should resolve through
 * `t('namespace.key')` rather than being hard-coded in JSX, so that adding a
 * locale later is a matter of dropping in a new resource bundle — no component
 * changes. English is the complete reference bundle; see ./README.md.
 *
 * Import this module once, before the React tree renders (see main.tsx).
 */
export const resources = {
  en: { translation: en }
} as const;

export const defaultNS = 'translation';

void i18n.use(initReactI18next).init({
  resources,
  lng: 'en',
  fallbackLng: 'en',
  defaultNS,
  interpolation: {
    // React already escapes rendered values.
    escapeValue: false
  },
  returnNull: false
});

export default i18n;
