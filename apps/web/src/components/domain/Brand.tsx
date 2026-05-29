import { Hexagon } from 'lucide-react'

const SIZES = {
  sm: { icon: 'h-6 w-6', text: 'text-base' },
  lg: { icon: 'h-8 w-8', text: 'text-2xl' },
} as const

interface Props {
  size?: keyof typeof SIZES
  className?: string
}

/**
 * Blue Steel brand lockup: an accent mark beside the wordmark. Presentational
 * only — reused by the global {@link AppBar} and the auth pages. The wordmark
 * text supplies the accessible name; the mark is decorative.
 */
export function Brand({ size = 'sm', className }: Props) {
  const s = SIZES[size]
  return (
    <span className={['inline-flex items-center gap-2', className].filter(Boolean).join(' ')}>
      <Hexagon className={`${s.icon} shrink-0 text-blue-500`} aria-hidden />
      <span className={`${s.text} font-semibold tracking-tight text-slate-900`}>Blue Steel</span>
    </span>
  )
}
