import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { useCosignedProposals } from '@/api/proposals'
import { useCampaignStore } from '@/store/campaignStore'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { ProposalThreadSkeleton } from '@/components/domain/ProposalThreadSkeleton'
import { ProposalReviewCard, type DecisionOutcome } from './ProposalReviewCard'

/** The Exploration path to the target entity's profile, where an approved version appears in history. */
function targetProfilePath(campaignId: string, outcome: DecisionOutcome): string {
  const segment = outcome.proposal.targetType === 'ACTOR' ? 'entities' : 'spaces'
  return `/campaigns/${campaignId}/explore/${segment}/${outcome.proposal.targetId}`
}

/**
 * GM-only review queue for co-signed proposals (F5.9.3). Lists each proposal as a {@link
 * ProposalReviewCard} to approve-with-edit or veto. Non-GM members are redirected to the campaign
 * home. After an approval, a banner links to the target profile showing the new version (D-107).
 */
export function ProposalReviewQueuePage() {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  const activeRole = useCampaignStore((s) => s.activeRole)
  const { data, isLoading, isError } = useCosignedProposals(campaignId ?? '')
  const [outcome, setOutcome] = useState<DecisionOutcome | null>(null)

  if (activeRole !== 'gm') {
    return <Navigate to={`/campaigns/${campaignId ?? ''}`} replace />
  }

  const proposals = data?.proposals ?? []

  return (
    <section aria-labelledby="review-queue-heading" className="mx-auto max-w-3xl">
      <h1 id="review-queue-heading" className="mb-1 text-2xl font-semibold text-slate-900">
        Proposal review queue
      </h1>
      <p className="mb-6 text-sm text-slate-500">
        Co-signed proposals awaiting your decision. Approve (optionally editing the change) or veto.
      </p>

      {outcome && (
        <div className="mb-4">
          <InlineBanner
            variant="success"
            message={
              outcome.resultingEntityVersionId
                ? 'Proposal approved — a new version was written.'
                : 'Proposal vetoed.'
            }
            onDismiss={() => setOutcome(null)}
          />
          {outcome.resultingEntityVersionId && campaignId && (
            <Link
              to={targetProfilePath(campaignId, outcome)}
              className="mt-2 inline-block text-sm text-blue-500 underline-offset-4 hover:underline"
            >
              View the updated {outcome.proposal.targetType.toLowerCase()} profile
            </Link>
          )}
        </div>
      )}

      {isLoading && <ProposalThreadSkeleton />}

      {isError && (
        <InlineBanner
          variant="error"
          message="Could not load the review queue. Please refresh the page."
          onDismiss={() => undefined}
        />
      )}

      {!isLoading && !isError && (
        <div className="space-y-4">
          {proposals.length === 0 ? (
            <p className="text-sm text-slate-500">No proposals are awaiting review.</p>
          ) : (
            proposals.map((proposal) => (
              <ProposalReviewCard
                key={proposal.proposalId}
                proposal={proposal}
                onDecided={setOutcome}
              />
            ))
          )}
        </div>
      )}
    </section>
  )
}
