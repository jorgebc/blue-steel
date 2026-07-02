/**
 * Loading placeholder for the Timeline feed, shaped to the `EventCard` row (name + type badge over a
 * details line) so swapping in real events causes zero layout shift (D-086 — no spinners in primary
 * content).
 */
import { useTranslation } from 'react-i18next'

export function TimelineSkeleton() {
  const { t } = useTranslation()
  return (
    <div role="status" aria-label={t('exploration.timeline.loadingAria')} className="space-y-3">
      {Array.from({ length: 5 }).map((_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 rounded-2xl border border-border bg-surface p-4 shadow-sm"
        >
          <div className="flex-1 space-y-2">
            {/* event name */}
            <div className="h-4 w-48 rounded bg-muted animate-pulse" />
            {/* actors / space / session line */}
            <div className="h-3 w-64 rounded bg-muted animate-pulse" />
          </div>
          {/* event-type badge */}
          <div className="h-5 w-16 rounded-full bg-muted animate-pulse shrink-0" />
        </div>
      ))}
    </div>
  )
}
