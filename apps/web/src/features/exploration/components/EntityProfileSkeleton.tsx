/**
 * Loading placeholder for an entity profile, shaped to the header + current-state block + version
 * history rows so the real profile drops in without layout shift (D-086).
 */
import { useTranslation } from 'react-i18next'

export function EntityProfileSkeleton() {
  const { t } = useTranslation()
  return (
    <div role="status" aria-label={t('exploration.loadingProfileAria')} className="space-y-6">
      {/* header: name + type */}
      <div className="space-y-2">
        <div className="h-7 w-56 rounded bg-muted animate-pulse" />
        <div className="h-4 w-24 rounded bg-muted animate-pulse" />
      </div>

      {/* current state card */}
      <div className="space-y-3 rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <div className="h-4 w-32 rounded bg-muted animate-pulse" />
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="flex gap-4">
            <div className="h-3 w-24 rounded bg-muted animate-pulse" />
            <div className="h-3 w-40 rounded bg-muted animate-pulse" />
          </div>
        ))}
      </div>

      {/* version history */}
      <div className="space-y-2">
        <div className="h-4 w-36 rounded bg-muted animate-pulse" />
        {Array.from({ length: 2 }).map((_, i) => (
          <div
            key={i}
            className="h-12 rounded-xl border border-border bg-surface shadow-sm animate-pulse"
          />
        ))}
      </div>
    </div>
  )
}
