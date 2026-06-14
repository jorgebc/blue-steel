/**
 * Helpers for editing a proposal's flat field delta (D-104) against a baseline map. A delta is a flat
 * `{ field: value }` object; only primitive fields (string/number/boolean) are editable in v2, so
 * structured (array/object) fields are surfaced read-only and never enter the delta.
 */

/** A field the UI renders as an editable text input. */
export function isEditablePrimitive(value: unknown): value is string | number | boolean {
  return typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean'
}

/** Renders a primitive (or null/undefined) field value as the string shown in a text input. */
export function toInputValue(value: unknown): string {
  return value == null ? '' : String(value)
}

/** Coerces an edited string back toward the baseline field's primitive type to avoid type drift. */
export function coerceToBaselineType(baseline: unknown, raw: string): string | number | boolean {
  if (typeof baseline === 'boolean') {
    if (raw === 'true') return true
    if (raw === 'false') return false
    return raw
  }
  if (typeof baseline === 'number') {
    const n = Number(raw)
    return raw.trim() !== '' && Number.isFinite(n) ? n : raw
  }
  return raw
}

/**
 * The changed-field subset: every key in `edited` whose stringified value differs from the baseline,
 * with the new value coerced toward the baseline's primitive type. Used to build a submission delta
 * (changes vs. the entity's current snapshot) and to detect whether a GM edited an existing delta.
 */
export function computeDelta(
  baseline: Record<string, unknown>,
  edited: Record<string, string>
): Record<string, unknown> {
  const delta: Record<string, unknown> = {}
  for (const [key, raw] of Object.entries(edited)) {
    if (raw !== toInputValue(baseline[key])) {
      delta[key] = coerceToBaselineType(baseline[key], raw)
    }
  }
  return delta
}

/**
 * The full edited map (every key in `edited`, coerced toward the baseline's type). A GM's
 * `editedDelta` replaces the author's delta wholesale (D-110), so it must carry every field, not just
 * the changed ones.
 */
export function buildFullDelta(
  baseline: Record<string, unknown>,
  edited: Record<string, string>
): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [key, raw] of Object.entries(edited)) {
    out[key] = coerceToBaselineType(baseline[key], raw)
  }
  return out
}

/** The editable string seed map for a baseline: only its primitive fields, stringified. */
export function editableSeed(baseline: Record<string, unknown>): Record<string, string> {
  const seed: Record<string, string> = {}
  for (const [key, value] of Object.entries(baseline)) {
    if (isEditablePrimitive(value)) seed[key] = toInputValue(value)
  }
  return seed
}
