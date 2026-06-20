/** A named accent-color preset for the initials avatar (D-100). */
export interface AccentColor {
  name: string
  hex: string
}

/**
 * The bounded set of avatar accent colors a user can choose from (the palette is settled here per
 * the Phase-8 gate). Hex values are drawn from the Tailwind 500 ramp; each is readable with the
 * avatar's auto-contrast foreground. Blue is the default and matches the {@code InitialsAvatar}
 * fallback.
 */
export const ACCENT_PALETTE: AccentColor[] = [
  { name: 'Blue', hex: '#3b82f6' },
  { name: 'Violet', hex: '#8b5cf6' },
  { name: 'Pink', hex: '#ec4899' },
  { name: 'Red', hex: '#ef4444' },
  { name: 'Amber', hex: '#f59e0b' },
  { name: 'Emerald', hex: '#10b981' },
  { name: 'Teal', hex: '#14b8a6' },
  { name: 'Slate', hex: '#64748b' },
]

/** Accent applied when the user has not chosen one — kept in sync with {@code InitialsAvatar}. */
export const DEFAULT_ACCENT = ACCENT_PALETTE[0].hex
