/**
 * Loading placeholder for an entity list, shaped to the `EntitySummary` row (name + version badge)
 * so swapping in real data causes zero layout shift (D-086 — no spinners in primary content).
 */
import { useTranslation } from 'react-i18next'

export function EntityListSkeleton() {
  const { t } = useTranslation()
  return (
    <div role="status" aria-label={t('exploration.loadingEntitiesAria')} className="space-y-3">
      {Array.from({ length: 6 }).map((_, i) => (
        <div
          key={i}
          className="flex items-center gap-4 rounded-2xl border border-border bg-surface p-4 shadow-sm"
        >
          <div className="flex-1 space-y-2">
            {/* name */}
            <div className="h-4 w-40 rounded bg-muted animate-pulse" />
            {/* updated-session line */}
            <div className="h-3 w-28 rounded bg-muted animate-pulse" />
          </div>
          {/* version badge */}
          <div className="h-5 w-12 rounded-full bg-muted animate-pulse shrink-0" />
        </div>
      ))}
    </div>
  )
}
