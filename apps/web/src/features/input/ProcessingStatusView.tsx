import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useSessionStatus } from '@/api/sessions'
import { InlineBanner } from '@/components/domain/InlineBanner'

interface Props {
  campaignId: string
  sessionId: string
}

/** Skeleton mirroring the eventual diff-review header while extraction runs. */
function ProcessingSkeleton() {
  return (
    <div className="rounded-2xl bg-surface p-6 shadow-sm">
      <div className="mb-6 h-6 w-1/3 rounded bg-muted animate-pulse" />
      <div className="space-y-3">
        <div className="h-4 w-3/4 rounded bg-muted animate-pulse" />
        <div className="h-3 w-1/2 rounded bg-muted animate-pulse" />
        <div className="h-3 w-2/3 rounded bg-muted animate-pulse" />
      </div>
      <p aria-live="polite" className="mt-6 text-sm text-muted-foreground">
        Processing your session…
      </p>
    </div>
  )
}

/**
 * Polls a submitted session and renders each pipeline state: a skeleton while
 * `PROCESSING`, navigation to diff review on `DRAFT`, and an error banner on a
 * `FAILED` session or a fetch failure.
 */
export function ProcessingStatusView({ campaignId, sessionId }: Props) {
  const navigate = useNavigate()
  const { data, isError } = useSessionStatus(campaignId, sessionId, true)
  const [dismissed, setDismissed] = useState(false)

  useEffect(() => {
    if (data?.status === 'DRAFT') {
      navigate(`/campaigns/${campaignId}/sessions/${sessionId}/diff`)
    }
  }, [data?.status, campaignId, sessionId, navigate])

  if (dismissed) return null

  if (isError) {
    return (
      <InlineBanner
        variant="error"
        message="We couldn't check your session's status. Please try again."
        onDismiss={() => setDismissed(true)}
      />
    )
  }

  if (data?.status === 'FAILED') {
    const detail = [data.failureReason, data.message].filter(Boolean).join(' — ')
    return (
      <InlineBanner
        variant="error"
        message={`Processing failed${detail ? `: ${detail}` : '.'}`}
        onDismiss={() => setDismissed(true)}
      />
    )
  }

  return <ProcessingSkeleton />
}
