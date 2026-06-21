import './localStorageShim' // must run before any persisted store is created
import '@testing-library/jest-dom'
import 'vitest-axe/extend-expect' // types only — runtime no-op in v0.1.0
import * as axeMatchers from 'vitest-axe/matchers'
import { afterEach, expect } from 'vitest'
import i18n from '@/i18n' // initialise the global i18n runtime for all tests
expect.extend(axeMatchers) // actual runtime registration

// Reset the UI language after every test so a test that switches to a non-default
// locale does not leak into the next one.
afterEach(() => {
  i18n.changeLanguage('en')
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

// jsdom has no ResizeObserver, which Radix UI primitives (e.g. Select) measure with on mount.
if (!('ResizeObserver' in globalThis)) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

// jsdom has no matchMedia, which useApplyTheme calls to resolve the `system`
// colour scheme. Default to "light" (matches: false); tests that exercise the
// system-follow path replace this with a controllable stub.
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as unknown as MediaQueryList
}
