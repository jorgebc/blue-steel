type Size = 'sm' | 'md' | 'lg'

interface Props {
  displayName?: string | null
  email: string
  accentColor?: string | null
  size?: Size
}

/** Default accent when the user has not chosen one (UX Constitution — Electric Blue). */
const DEFAULT_ACCENT = '#3b82f6'

const SIZE_CLASS: Record<Size, string> = {
  sm: 'h-8 w-8 text-xs',
  md: 'h-10 w-10 text-sm',
  lg: 'h-12 w-12 text-base',
}

/** First letters of the first two words of the display name, or the email's first letter. */
function deriveInitials(displayName: string | null | undefined, email: string): string {
  const name = displayName?.trim()
  if (name) {
    return name
      .split(/\s+/)
      .slice(0, 2)
      .map((word) => word[0])
      .join('')
      .toUpperCase()
  }
  return (email.trim()[0] ?? '?').toUpperCase()
}

/** Picks white or near-black foreground from the accent's perceived luminance (WCAG relative luminance). */
function readableForeground(hex: string): string {
  const normalized = hex.replace('#', '')
  if (normalized.length !== 6) return '#ffffff'
  const channel = (start: number) => {
    const srgb = parseInt(normalized.slice(start, start + 2), 16) / 255
    return srgb <= 0.03928 ? srgb / 12.92 : ((srgb + 0.055) / 1.055) ** 2.4
  }
  const luminance = 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4)
  return luminance > 0.179 ? '#0f172a' : '#ffffff'
}

/**
 * Round identity badge rendered from initials over a chosen accent color (D-100 — no uploaded
 * images). Initials come from the display name, falling back to the email; the accent defaults to
 * Electric Blue. The glyph is decorative — the accessible name carries the user's name or email.
 */
export function InitialsAvatar({ displayName, email, accentColor, size = 'md' }: Props) {
  const backgroundColor = accentColor || DEFAULT_ACCENT
  const initials = deriveInitials(displayName, email)
  const label = displayName?.trim() || email

  return (
    <span
      role="img"
      aria-label={label}
      style={{ backgroundColor, color: readableForeground(backgroundColor) }}
      className={`inline-flex shrink-0 items-center justify-center rounded-full font-semibold ${SIZE_CLASS[size]}`}
    >
      <span aria-hidden>{initials}</span>
    </span>
  )
}
