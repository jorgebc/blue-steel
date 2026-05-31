/** Renders an arbitrary diff field value (string/number/boolean/object) as display text. */
export function formatFieldValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

/** Encodes a field value into an editable input string (strings stay literal; everything else is JSON). */
export function encodeFieldValue(value: unknown): string {
  if (value === undefined) return ''
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

/**
 * Decodes an edited input string back to the original field's type: a string-typed
 * field stays a literal string; anything else is parsed as JSON (falling back to the
 * raw string if it is not valid JSON), so numbers/booleans/objects round-trip.
 */
export function decodeFieldValue(raw: string, original: unknown): unknown {
  if (typeof original === 'string') return raw
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}
