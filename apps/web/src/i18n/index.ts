import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { useSettingsStore } from '@/store/settingsStore'
import en from './locales/en.json'
import es from './locales/es.json'

/**
 * Global i18next runtime for the UI. Initialised synchronously on import so the
 * first render already has the right language. The active language is driven by
 * the user's {@code uiLocale} in {@link useSettingsStore} (persisted to
 * localStorage, hydrated from the server on login per D-101); missing keys fall
 * back to English. This is the per-user UI-locale axis only — campaign content
 * language is a separate concern (D-099).
 */
i18n.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    es: { translation: es },
  },
  lng: useSettingsStore.getState().uiLocale,
  fallbackLng: 'en',
  interpolation: { escapeValue: false }, // React already escapes
})

// Keep the active language in sync with the store so changing the locale from
// the account menu or settings page re-renders without a full page reload.
useSettingsStore.subscribe((state) => {
  if (i18n.language !== state.uiLocale) {
    i18n.changeLanguage(state.uiLocale)
  }
})

// Mirror the active language onto <html lang> so assistive tech, the browser
// spell-checker, and translation tooling see the real page language (WCAG 3.1).
// The store subscription above drives this via languageChanged; the explicit
// assignment covers the initial language set at init.
i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng
})
document.documentElement.lang = i18n.language

export default i18n
