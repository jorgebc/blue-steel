import '@testing-library/jest-dom'
import 'vitest-axe/extend-expect' // types only — runtime no-op in v0.1.0
import * as axeMatchers from 'vitest-axe/matchers'
import { expect } from 'vitest'
expect.extend(axeMatchers) // actual runtime registration

// jsdom doesn't initialise a spec-compliant localStorage when the
// --localstorage-file flag has no valid path. Provide a minimal in-memory
// shim so Zustand's persist middleware (used by uiStore) can call
// getItem/setItem/removeItem/clear without throwing.
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

// jsdom implements neither Pointer Capture nor scrollIntoView, which Radix UI
// primitives (e.g. Select) call when opening. Provide no-op shims so they can be
// driven in tests.
if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {}
}
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}
