/** Renders an arbitrary diff field value (string/number/boolean/object) as display text. */
export function formatFieldValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

/** Which input control a field should render in the edit overlay. */
export type FieldControl = 'array' | 'textarea' | 'input'

/** Field keys that hold prose and read better in a multi-line textarea. */
const LONG_TEXT_KEYS = new Set(['description', 'summary', 'backstory', 'notes', 'history'])

/** Chooses the edit control for a field from its key and original value. */
export function fieldControl(key: string, value: unknown): FieldControl {
  if (Array.isArray(value)) return 'array'
  if (typeof value === 'string' && (LONG_TEXT_KEYS.has(key) || value.includes('\n') || value.length > 120)) {
    return 'textarea'
  }
  return 'input'
}

/**
 * Encodes a field value into an editable input string: strings stay literal,
 * arrays become a comma-separated list, everything else is JSON.
 */
export function encodeFieldValue(value: unknown): string {
  if (value === undefined) return ''
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return value.join(', ')
  return JSON.stringify(value)
}

/**
 * Decodes an edited input string back to the original field's type: a string-typed
 * field stays a literal string; an array-typed field is split on commas (trimmed,
 * empties dropped); anything else is parsed as JSON (falling back to the raw string
 * if it is not valid JSON), so numbers/booleans/objects round-trip.
 */
export function decodeFieldValue(raw: string, original: unknown): unknown {
  if (typeof original === 'string') return raw
  if (Array.isArray(original)) {
    return raw
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
  }
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}
