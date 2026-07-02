import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ChevronLeft } from 'lucide-react'
import { useSessionDetail } from '@/api/sessions'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Badge } from '@/components/ui/badge'
import type { SessionStatus } from '@/types/session'

const STATUS_CLASS: Record<SessionStatus, string> = {
  PENDING: 'bg-muted text-muted-foreground',
  PROCESSING: 'bg-info-subtle text-info-foreground',
  DRAFT: 'bg-warning-subtle text-warning-foreground',
  COMMITTED: 'bg-success-subtle text-success-foreground',
  FAILED: 'bg-error-subtle text-error-foreground',
  DISCARDED: 'bg-muted text-muted-foreground',
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

function SessionDetailSkeleton() {
  const { t } = useTranslation()
  return (
    <div role="status" aria-label={t('sessions.loadingDetailAria')} className="space-y-6">
      <div className="space-y-2">
        <div className="h-7 w-40 animate-pulse rounded bg-muted" />
        <div className="h-5 w-24 animate-pulse rounded-full bg-muted" />
      </div>
      <div className="h-40 animate-pulse rounded-2xl bg-muted" />
    </div>
  )
}

/**
 * Exploration → read-only committed-session detail (F4.8.3), reached by click-through from an
 * entity profile's "Appears in sessions" section. Shows the session's sequence number, status,
 * committed date and raw narrative summary. Strictly read-only — distinct from the editable
 * `sessions/:sessionId/diff` review route (D-010).
 */
export function SessionDetailPage() {
  const { t } = useTranslation()
  const { campaignId, sessionId } = useParams<{ campaignId: string; sessionId: string }>()
  const navigate = useNavigate()
  const { data, isLoading, isError } = useSessionDetail(campaignId ?? '', sessionId ?? '')

  return (
    <main className="mx-auto max-w-3xl p-8">
      <Link
        to={`/campaigns/${campaignId}/sessions`}
        className="mb-6 inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors duration-200 hover:text-foreground"
      >
        <ChevronLeft className="h-4 w-4" aria-hidden />
        {t('sessions.backToSessions')}
      </Link>

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message={t('sessions.detailLoadError')}
            onDismiss={() => navigate(0)}
          />
        </div>
      )}

      {isLoading && <SessionDetailSkeleton />}

      {!isLoading && !isError && data && (
        <div className="space-y-6">
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-2">
              <h1 className="text-2xl font-semibold text-foreground">
                {data.sequenceNumber !== null
                  ? t('sessions.sessionNumber', { sequence: data.sequenceNumber })
                  : t('sessions.sessionFallback')}
              </h1>
              <Badge className={STATUS_CLASS[data.status]} variant="outline">
                {t(`sessions.status.${data.status.toLowerCase()}`)}
              </Badge>
            </div>
            {data.committedAt && (
              <p className="shrink-0 text-sm text-muted-foreground">
                {t('sessions.committedOn', { date: formatDate(data.committedAt) })}
              </p>
            )}
          </div>

          <div className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
            <h2 className="mb-3 text-sm font-semibold text-foreground">
              {t('sessions.narrativeSummary')}
            </h2>
            {data.narrativeSummary ? (
              <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground">
                {data.narrativeSummary}
              </p>
            ) : (
              <p className="text-sm text-muted-foreground">{t('sessions.noNarrative')}</p>
            )}
          </div>
        </div>
      )}
    </main>
  )
}
