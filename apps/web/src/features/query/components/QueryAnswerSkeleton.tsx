/**
 * Loading placeholder for the Query Mode answer area, shown while a query is in flight.
 * Mirrors the shape of {@link AnswerDisplay} — an answer paragraph plus a few citation
 * lines — so there is zero layout shift when the real answer arrives (D-086, no spinner).
 */
import { useTranslation } from 'react-i18next'

export function QueryAnswerSkeleton() {
  const { t } = useTranslation()
  return (
    <div role="status" aria-label={t('query.answerSkeletonAria')} className="space-y-6">
      {/* Answer paragraph */}
      <div className="space-y-2">
        <div className="h-4 w-full animate-pulse rounded bg-muted" />
        <div className="h-4 w-11/12 animate-pulse rounded bg-muted" />
        <div className="h-4 w-4/5 animate-pulse rounded bg-muted" />
      </div>

      {/* Citation lines */}
      <div className="space-y-3">
        <div className="h-3 w-20 animate-pulse rounded bg-muted" />
        <div className="h-4 w-2/3 animate-pulse rounded bg-muted" />
        <div className="h-4 w-3/5 animate-pulse rounded bg-muted" />
      </div>
    </div>
  )
}
