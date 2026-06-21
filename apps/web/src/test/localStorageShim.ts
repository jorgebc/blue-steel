// jsdom doesn't initialise a spec-compliant localStorage when the
// --localstorage-file flag has no valid path. Provide a minimal in-memory
// shim so Zustand's persist middleware (used by uiStore / settingsStore) can
// call getItem/setItem/removeItem/clear without throwing.
//
// This lives in its own module, imported first in setup.ts, so the shim is in
// place before any store (e.g. the persisted settingsStore pulled in via the
// i18n runtime) is created and reads its storage on rehydration.
const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: (key: string) => store[key] ?? null,
    setItem: (key: string, value: string) => {
      store[key] = value
    },
    removeItem: (key: string) => {
      delete store[key]
    },
    clear: () => {
      store = {}
    },
    get length() {
      return Object.keys(store).length
    },
    key: (index: number) => Object.keys(store)[index] ?? null,
  }
})()

Object.defineProperty(window, 'localStorage', {
  value: localStorageMock,
  writable: true,
})
