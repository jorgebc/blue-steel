import { Link, useNavigate, useParams } from 'react-router-dom'
import { ChevronLeft } from 'lucide-react'
import { useSessionDetail } from '@/api/sessions'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Badge } from '@/components/ui/badge'
import type { SessionStatus } from '@/types/session'

const STATUS_LABEL: Record<SessionStatus, string> = {
  PENDING: 'Pending',
  PROCESSING: 'Processing',
  DRAFT: 'Draft',
  COMMITTED: 'Committed',
  FAILED: 'Failed',
  DISCARDED: 'Discarded',
}

const STATUS_CLASS: Record<SessionStatus, string> = {
  PENDING: 'bg-slate-100 text-slate-600',
  PROCESSING: 'bg-blue-50 text-blue-600',
  DRAFT: 'bg-amber-50 text-amber-700',
  COMMITTED: 'bg-green-50 text-green-700',
  FAILED: 'bg-red-50 text-red-700',
  DISCARDED: 'bg-slate-100 text-slate-400',
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

function SessionDetailSkeleton() {
  return (
    <div role="status" aria-label="Loading session" className="space-y-6">
      <div className="space-y-2">
        <div className="h-7 w-40 animate-pulse rounded bg-slate-200" />
        <div className="h-5 w-24 animate-pulse rounded-full bg-slate-200" />
      </div>
      <div className="h-40 animate-pulse rounded-2xl bg-slate-200" />
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
  const { campaignId, sessionId } = useParams<{ campaignId: string; sessionId: string }>()
  const navigate = useNavigate()
  const { data, isLoading, isError } = useSessionDetail(campaignId ?? '', sessionId ?? '')

  return (
    <main className="mx-auto max-w-3xl p-8">
      <Link
        to={`/campaigns/${campaignId}/sessions`}
        className="mb-6 inline-flex items-center gap-1 text-sm text-slate-500 transition-colors duration-200 hover:text-slate-900"
      >
        <ChevronLeft className="h-4 w-4" aria-hidden />
        Back to sessions
      </Link>

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message="Could not load this session. Please refresh the page."
            onDismiss={() => navigate(0)}
          />
        </div>
      )}

      {isLoading && <SessionDetailSkeleton />}

      {!isLoading && !isError && data && (
        <div className="space-y-6">
          <div className="flex items-start justify-between gap-4">
            <div className="space-y-2">
              <h1 className="text-2xl font-semibold text-slate-900">
                {data.sequenceNumber !== null ? `Session #${data.sequenceNumber}` : 'Session'}
              </h1>
              <Badge className={STATUS_CLASS[data.status]} variant="outline">
                {STATUS_LABEL[data.status]}
              </Badge>
            </div>
            {data.committedAt && (
              <p className="shrink-0 text-sm text-slate-500">
                Committed {formatDate(data.committedAt)}
              </p>
            )}
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="mb-3 text-sm font-semibold text-slate-700">Narrative summary</h2>
            {data.narrativeSummary ? (
              <p className="whitespace-pre-wrap text-sm leading-relaxed text-slate-900">
                {data.narrativeSummary}
              </p>
            ) : (
              <p className="text-sm text-slate-500">No narrative summary recorded.</p>
            )}
          </div>
        </div>
      )}
    </main>
  )
}
