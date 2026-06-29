import { X } from 'lucide-react'
import { useEffect } from 'react'

type Variant = 'success' | 'warning' | 'error' | 'info'

const STYLES: Record<Variant, string> = {
  success: 'bg-success-subtle border-success/30 text-success-foreground',
  warning: 'bg-warning-subtle border-warning/30 text-warning-foreground',
  error: 'bg-error-subtle border-error/30 text-error-foreground',
  info: 'bg-info-subtle border-info/30 text-info-foreground',
}

interface Props {
  variant: Variant
  message: string
  onDismiss: () => void
}

export function InlineBanner({ variant, message, onDismiss }: Props) {
  useEffect(() => {
    if (variant === 'error') return
    const id = setTimeout(onDismiss, 8000)
    return () => clearTimeout(id)
  }, [variant, onDismiss])

  return (
    <div
      role="alert"
      aria-live="polite"
      className={[
        'flex items-start gap-3 rounded-lg border p-4',
        'animate-in slide-in-from-top-2 duration-200',
        STYLES[variant],
      ].join(' ')}
    >
      <span className="flex-1 text-sm">{message}</span>
      <button
        onClick={onDismiss}
        aria-label="Dismiss"
        className="shrink-0 opacity-70 hover:opacity-100"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  )
}
