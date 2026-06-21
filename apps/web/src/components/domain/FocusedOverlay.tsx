import type { ReactNode } from 'react'
import { useEscapeKey } from '@/hooks/useEscapeKey'

interface Props {
  open: boolean
  onClose: () => void
  children: ReactNode
  className?: string
  /** Accessible name for the dialog (required for screen readers). */
  ariaLabel?: string
}

/**
 * The app-wide replacement for modals/dialogs (D-082, UX Constitution §4): the
 * focused element elevates in place above a dimmed, blurred backdrop. Closes on
 * Escape and on backdrop click. Renders nothing while closed.
 */
export function FocusedOverlay({ open, onClose, children, className, ariaLabel }: Props) {
  useEscapeKey(onClose)

  if (!open) return null

  return (
    <>
      <div
        className="fixed inset-0 z-40 bg-slate-900/30 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden="true"
      />
      <div className="pointer-events-none fixed inset-0 z-50 flex items-center justify-center">
        <div
          role="dialog"
          aria-modal="true"
          aria-label={ariaLabel}
          className={[
            'pointer-events-auto overflow-hidden rounded-2xl shadow-xl ring-2 ring-ring/50',
            'transition-shadow duration-200 ease-out',
            className,
          ]
            .filter(Boolean)
            .join(' ')}
        >
          {children}
        </div>
      </div>
    </>
  )
}
