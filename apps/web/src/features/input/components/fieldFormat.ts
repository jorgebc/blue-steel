/** Renders an arbitrary diff field value (string/number/boolean/object) as display text. */
export function formatFieldValue(value: unknown): string {
  if (value === null || value === undefined) return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
