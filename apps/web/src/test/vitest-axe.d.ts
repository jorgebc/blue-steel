import type { AxeMatchers } from 'vitest-axe'

// vitest-axe v0.1.0 targets the old `Vi` namespace, which no longer exists in Vitest 4.
// This shim adds toHaveNoViolations to the correct `vitest` module declaration.
declare module 'vitest' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface Assertion extends AxeMatchers {}
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type
  interface AsymmetricMatchersContaining extends AxeMatchers {}
}
