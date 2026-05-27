import { X } from 'lucide-react'
import { useEffect } from 'react'

type Variant = 'success' | 'warning' | 'error' | 'info'

const STYLES: Record<Variant, string> = {
  success: 'bg-green-50 border-green-200 text-green-800',
  warning: 'bg-amber-50 border-amber-200 text-amber-800',
  error: 'bg-red-50 border-red-200 text-red-800',
  info: 'bg-blue-50 border-blue-200 text-blue-800',
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
