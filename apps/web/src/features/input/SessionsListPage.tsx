import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ChevronLeft, History } from 'lucide-react'
import { useSessions, useDiscardSession } from '@/api/sessions'
import { useCampaignStore } from '@/store/campaignStore'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { DiscardConfirmOverlay } from './components/DiscardConfirmOverlay'
import type { SessionSummary } from '@/types/session'

// ─── Status badge ─────────────────────────────────────────────────────────────

const STATUS_LABEL: Record<SessionSummary['status'], string> = {
  PENDING: 'Pending',
  PROCESSING: 'Processing',
  DRAFT: 'Draft',
  COMMITTED: 'Committed',
  FAILED: 'Failed',
  DISCARDED: 'Discarded',
}

const STATUS_CLASS: Record<SessionSummary['status'], string> = {
  PENDING: 'bg-muted text-muted-foreground',
  PROCESSING: 'bg-info-subtle text-info-foreground',
  DRAFT: 'bg-warning-subtle text-warning-foreground',
  COMMITTED: 'bg-success-subtle text-success-foreground',
  FAILED: 'bg-error-subtle text-error-foreground',
  DISCARDED: 'bg-muted text-muted-foreground',
}

function StatusBadge({ status }: { status: SessionSummary['status'] }) {
  return (
    <Badge className={STATUS_CLASS[status]} variant="outline">
      {STATUS_LABEL[status]}
    </Badge>
  )
}

// ─── Skeleton ────────────────────────────────────────────────────────────────

function SessionRowSkeleton() {
  return (
    <div className="flex items-center gap-4 rounded-2xl border border-border bg-surface p-4 shadow-sm">
      {/* sequence number */}
      <div className="h-4 w-6 rounded bg-muted animate-pulse shrink-0" />
      <div className="flex-1 space-y-2">
        {/* status badge */}
        <div className="h-5 w-20 rounded-full bg-muted animate-pulse" />
        {/* date */}
        <div className="h-3 w-32 rounded bg-muted animate-pulse" />
      </div>
      {/* action placeholder */}
      <div className="h-9 w-24 rounded-lg bg-muted animate-pulse" />
    </div>
  )
}

// ─── Session row ──────────────────────────────────────────────────────────────

interface RowProps {
  session: SessionSummary
  campaignId: string
  activeRole: string | null
  onDiscard: (sessionId: string) => void
}

function SessionRow({ session, campaignId, activeRole, onDiscard }: RowProps) {
  const date = session.committedAt ?? session.createdAt
  const formatted = new Date(date).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
  const isDraft = session.status === 'DRAFT'
  const canEditDraft = isDraft && (activeRole === 'gm' || activeRole === 'editor')
  const canDiscard = canEditDraft

  return (
    <div className="flex items-center gap-4 rounded-2xl border border-border bg-surface p-4 shadow-sm transition-shadow duration-200 hover:shadow-md">
      <Link
        to={`/campaigns/${campaignId}/sessions/${session.sessionId}`}
        className="flex flex-1 items-center gap-4 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <span className="w-6 shrink-0 text-center text-sm font-medium text-muted-foreground">
          #{session.sequenceNumber}
        </span>
        <div className="flex flex-1 flex-col gap-1">
          <StatusBadge status={session.status} />
          <span className="text-xs text-muted-foreground">{formatted}</span>
        </div>
      </Link>
      <div className="flex shrink-0 gap-2">
        {canEditDraft && (
          <Link
            to={`/campaigns/${campaignId}/sessions/${session.sessionId}/diff`}
            className="inline-flex h-9 items-center rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-accent-hover transition-colors duration-200"
          >
            Resume
          </Link>
        )}
        {canDiscard && (
          <Button
            type="button"
            variant="outline"
            className="border-red-300 text-red-700 hover:bg-red-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950/40"
            onClick={() => onDiscard(session.sessionId)}
          >
            Discard
          </Button>
        )}
      </div>
    </div>
  )
}

// ─── Page ────────────────────────────────────────────────────────────────────

/**
 * Lists all sessions for the active campaign (committed, draft, failed). GMs and
 * editors can resume a draft directly and discard it from here (D-054).
 */
export function SessionsListPage() {
  const { campaignId } = useParams<{ campaignId: string }>()
  const navigate = useNavigate()
  const activeRole = useCampaignStore((s) => s.activeRole)

  const { data: sessions, isLoading, isError } = useSessions(campaignId ?? '', 0)

  const [discardId, setDiscardId] = useState<string | null>(null)
  const [discardError, setDiscardError] = useState(false)
  const { mutate: doDiscard, isPending: isDiscarding } = useDiscardSession(
    campaignId ?? '',
    discardId ?? ''
  )

  function handleDiscardConfirm() {
    doDiscard(undefined, {
      onSuccess() {
        setDiscardId(null)
      },
      onError() {
        setDiscardId(null)
        setDiscardError(true)
      },
    })
  }

  return (
    <main className="mx-auto max-w-3xl p-8">
      <Link
        to={`/campaigns/${campaignId}`}
        className="mb-6 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors duration-200"
      >
        <ChevronLeft className="h-4 w-4" aria-hidden />
        Campaign home
      </Link>

      <div className="mb-6 flex items-center gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-accent-subtle text-accent">
          <History className="h-5 w-5" aria-hidden />
        </div>
        <div>
          <h1 className="text-2xl font-semibold text-foreground">Session history</h1>
          <p className="text-sm text-muted-foreground">All sessions for this campaign.</p>
        </div>
      </div>

      {discardError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message="Failed to discard the draft. Please try again."
            onDismiss={() => setDiscardError(false)}
          />
        </div>
      )}

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message="Could not load sessions. Please refresh the page."
            onDismiss={() => navigate(0)}
          />
        </div>
      )}

      {isLoading && (
        <div role="status" aria-label="Loading sessions" className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <SessionRowSkeleton key={i} />
          ))}
        </div>
      )}

      {!isLoading && !isError && sessions?.length === 0 && (
        <p className="text-sm text-muted-foreground">No sessions yet.</p>
      )}

      {!isLoading && !isError && sessions && sessions.length > 0 && (
        <div className="space-y-3">
          {sessions.map((session) => (
            <SessionRow
              key={session.sessionId}
              session={session}
              campaignId={campaignId ?? ''}
              activeRole={activeRole}
              onDiscard={setDiscardId}
            />
          ))}
        </div>
      )}

      <DiscardConfirmOverlay
        open={discardId !== null}
        onClose={() => setDiscardId(null)}
        onConfirm={handleDiscardConfirm}
        isPending={isDiscarding}
      />
    </main>
  )
}
