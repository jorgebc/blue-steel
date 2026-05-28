import '@testing-library/jest-dom'
import 'vitest-axe/extend-expect' // types only — runtime no-op in v0.1.0
import * as axeMatchers from 'vitest-axe/matchers'
import { expect } from 'vitest'
expect.extend(axeMatchers) // actual runtime registration
