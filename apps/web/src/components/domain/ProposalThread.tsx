import { useState } from 'react'
import { useProposalsForTarget, useCoSignProposal } from '@/api/proposals'
import { ApiClientError } from '@/api/client'
import { useAuthStore } from '@/store/authStore'
import { useCampaignStore } from '@/store/campaignStore'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { ProposalStatusBadge } from '@/components/domain/ProposalStatusBadge'
import { ProposalThreadSkeleton } from '@/components/domain/ProposalThreadSkeleton'
import { Button } from '@/components/ui/button'
import type { Proposal, ProposalTargetType } from '@/types/proposal'

interface Props {
  targetType: ProposalTargetType
  targetId: string
}

type Feedback = { variant: 'success' | 'error'; message: string } | null

/** Renders a proposal's flat field delta as a compact key/value list. */
function DeltaList({ delta }: { delta: Record<string, unknown> }) {
  const entries = Object.entries(delta)
  if (entries.length === 0) return null
  return (
    <dl className="space-y-1 text-sm">
      {entries.map(([key, value]) => (
        <div key={key} className="flex gap-3">
          <dt className="w-32 shrink-0 font-medium text-slate-500">{key}</dt>
          <dd className="text-slate-900">{String(value)}</dd>
        </div>
      ))}
    </dl>
  )
}

/**
 * Per-entity proposal list shown on an actor/space profile (F5.8). Lists this target's proposals with
 * their status, and lets any non-author member co-sign an open proposal (D-109). The list is scoped
 * to this entity server-side (no client-side filtering, so nothing is missed beyond the first page).
 * Feedback via {@link InlineBanner} (no toasts); loading via {@link ProposalThreadSkeleton}.
 */
export function ProposalThread({ targetType, targetId }: Props) {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  const currentUserId = useAuthStore((s) => s.currentUser?.id)

  const { data, isLoading, isError } = useProposalsForTarget(
    campaignId ?? '',
    targetType,
    targetId
  )
  const coSign = useCoSignProposal(campaignId ?? '')

  const [feedback, setFeedback] = useState<Feedback>(null)

  const proposals = data?.proposals ?? []

  function canCoSign(proposal: Proposal): boolean {
    return proposal.status === 'OPEN' && currentUserId !== proposal.ownerId
  }

  function handleCoSign(proposalId: string) {
    setFeedback(null)
    coSign.mutate(proposalId, {
      onSuccess: () => setFeedback({ variant: 'success', message: 'Proposal co-signed.' }),
      onError: (err) => {
        const message =
          err instanceof ApiClientError
            ? (err.errors[0]?.message ?? "We couldn't co-sign this proposal. Try again.")
            : "We couldn't co-sign this proposal. Try again."
        setFeedback({ variant: 'error', message })
      },
    })
  }

  return (
    <section aria-label="Proposals" className="mt-8 border-t-2 border-dashed border-blue-300 pt-6">
      <header className="mb-4">
        <h2 className="text-sm font-semibold text-blue-900">Proposed changes</h2>
        <p className="text-xs text-blue-700">
          Member-submitted edits awaiting co-sign and GM review — not yet canonical world state.
        </p>
      </header>

      {feedback && (
        <div className="mb-4">
          <InlineBanner
            variant={feedback.variant}
            message={feedback.message}
            onDismiss={() => setFeedback(null)}
          />
        </div>
      )}

      {isLoading && <ProposalThreadSkeleton />}

      {isError && (
        <InlineBanner
          variant="error"
          message="Could not load proposals. Please refresh the page."
          onDismiss={() => undefined}
        />
      )}

      {!isLoading && !isError && (
        <div className="space-y-3">
          {proposals.length === 0 ? (
            <p className="text-sm text-slate-500">No proposals yet for this {targetType.toLowerCase()}.</p>
          ) : (
            proposals.map((proposal) => (
              <div
                key={proposal.proposalId}
                className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm"
              >
                <div className="mb-3 flex items-center justify-between gap-3">
                  <ProposalStatusBadge status={proposal.status} />
                  <span className="text-xs text-slate-400">
                    Expires {new Date(proposal.expiresAt).toLocaleDateString()}
                  </span>
                </div>
                <DeltaList delta={proposal.proposedDelta} />
                {canCoSign(proposal) && (
                  <div className="mt-4">
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => handleCoSign(proposal.proposalId)}
                      disabled={coSign.isPending}
                    >
                      Co-sign
                    </Button>
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </section>
  )
}
