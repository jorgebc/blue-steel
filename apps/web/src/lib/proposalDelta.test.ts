import { describe, it, expect } from 'vitest'
import {
  isEditablePrimitive,
  toInputValue,
  coerceToBaselineType,
  computeDelta,
  buildFullDelta,
  editableSeed,
} from './proposalDelta'

describe('isEditablePrimitive', () => {
  it('accepts strings, numbers, and booleans', () => {
    expect(isEditablePrimitive('x')).toBe(true)
    expect(isEditablePrimitive(3)).toBe(true)
    expect(isEditablePrimitive(false)).toBe(true)
  })

  it('rejects arrays, objects, and null', () => {
    expect(isEditablePrimitive(['a'])).toBe(false)
    expect(isEditablePrimitive({ a: 1 })).toBe(false)
    expect(isEditablePrimitive(null)).toBe(false)
  })
})

describe('toInputValue', () => {
  it('stringifies primitives and renders null/undefined as empty', () => {
    expect(toInputValue('x')).toBe('x')
    expect(toInputValue(3)).toBe('3')
    expect(toInputValue(null)).toBe('')
    expect(toInputValue(undefined)).toBe('')
  })
})

describe('coerceToBaselineType', () => {
  it('coerces toward a numeric baseline', () => {
    expect(coerceToBaselineType(1, '42')).toBe(42)
    expect(coerceToBaselineType(1, 'notnum')).toBe('notnum')
  })

  it('coerces toward a boolean baseline', () => {
    expect(coerceToBaselineType(true, 'false')).toBe(false)
    expect(coerceToBaselineType(false, 'true')).toBe(true)
    expect(coerceToBaselineType(true, 'maybe')).toBe('maybe')
  })

  it('keeps a string baseline as a string', () => {
    expect(coerceToBaselineType('a', 'b')).toBe('b')
  })
})

describe('computeDelta', () => {
  it('returns only the changed fields, coerced to baseline types', () => {
    const baseline = { name: 'Ari', age: 30, description: 'thief' }
    const edited = { name: 'Ari', age: '31', description: 'reformed thief' }
    expect(computeDelta(baseline, edited)).toEqual({ age: 31, description: 'reformed thief' })
  })

  it('returns an empty object when nothing changed', () => {
    expect(computeDelta({ name: 'Ari' }, { name: 'Ari' })).toEqual({})
  })
})

describe('buildFullDelta', () => {
  it('returns every edited field coerced toward the baseline type', () => {
    const baseline = { name: 'Ari', age: 30 }
    const edited = { name: 'Aria', age: '31' }
    expect(buildFullDelta(baseline, edited)).toEqual({ name: 'Aria', age: 31 })
  })
})

describe('editableSeed', () => {
  it('seeds only primitive fields as strings, skipping structured ones', () => {
    const seed = editableSeed({ name: 'Ari', aliases: ['Shadow'], active: true })
    expect(seed).toEqual({ name: 'Ari', active: 'true' })
  })
})
