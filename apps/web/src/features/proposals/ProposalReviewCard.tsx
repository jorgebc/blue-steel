import { useState } from 'react'
import { Loader2 } from 'lucide-react'
import { useDecideProposal } from '@/api/proposals'
import { ApiClientError } from '@/api/client'
import { useCampaignStore } from '@/store/campaignStore'
import { FocusedOverlay } from '@/components/domain/FocusedOverlay'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { ProposalStatusBadge } from '@/components/domain/ProposalStatusBadge'
import { DeltaFieldsEditor } from '@/components/domain/DeltaFieldsEditor'
import { Button } from '@/components/ui/button'
import { buildFullDelta, computeDelta, editableSeed } from '@/lib/proposalDelta'
import type { Proposal } from '@/types/proposal'

export interface DecisionOutcome {
  proposal: Proposal
  resultingEntityVersionId: string | null
}

interface Props {
  proposal: Proposal
  /** Called after a successful decision so the page can surface the outcome (the card then unmounts). */
  onDecided: (outcome: DecisionOutcome) => void
}

/**
 * One co-signed proposal in the GM review queue (F5.9). The GM can approve — optionally editing the
 * delta first (D-110) in a {@link FocusedOverlay} — or veto via a confirm overlay. Decision feedback
 * is inline; the resulting-version link is raised to the page via {@link Props.onDecided}.
 */
export function ProposalReviewCard({ proposal, onDecided }: Props) {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  const { mutate, isPending } = useDecideProposal(campaignId ?? '')

  const [mode, setMode] = useState<'approve' | 'veto' | null>(null)
  const [values, setValues] = useState<Record<string, string>>(() =>
    editableSeed(proposal.proposedDelta)
  )
  const [banner, setBanner] = useState<string | null>(null)

  function onError(err: unknown) {
    const message =
      err instanceof ApiClientError
        ? (err.errors[0]?.message ?? 'Could not record the decision. Try again.')
        : 'An unexpected error occurred. Please try again.'
    setBanner(message)
    setMode(null)
  }

  function handleApprove() {
    setBanner(null)
    const changed = computeDelta(proposal.proposedDelta, values)
    const editedDelta =
      Object.keys(changed).length > 0 ? buildFullDelta(proposal.proposedDelta, values) : undefined
    mutate(
      { proposalId: proposal.proposalId, body: { decision: 'APPROVE', editedDelta } },
      {
        onSuccess: (result) =>
          onDecided({ proposal, resultingEntityVersionId: result.resultingEntityVersionId }),
        onError,
      }
    )
  }

  function handleVeto() {
    setBanner(null)
    mutate(
      { proposalId: proposal.proposalId, body: { decision: 'REJECT' } },
      {
        onSuccess: () => onDecided({ proposal, resultingEntityVersionId: null }),
        onError,
      }
    )
  }

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-slate-900">
            {proposal.targetType === 'ACTOR' ? 'Actor' : 'Space'} change
          </p>
          <p className="text-xs text-slate-500">
            Submitted {new Date(proposal.createdAt).toLocaleDateString()}
          </p>
        </div>
        <ProposalStatusBadge status={proposal.status} />
      </div>

      {banner && (
        <div className="mb-4">
          <InlineBanner variant="error" message={banner} onDismiss={() => setBanner(null)} />
        </div>
      )}

      <dl className="mb-4 space-y-1 text-sm">
        {Object.entries(proposal.proposedDelta).map(([key, value]) => (
          <div key={key} className="flex gap-3">
            <dt className="w-32 shrink-0 font-medium text-slate-500">{key}</dt>
            <dd className="text-slate-900">{String(value)}</dd>
          </div>
        ))}
      </dl>

      <div className="flex justify-end gap-3">
        <Button type="button" variant="outline" onClick={() => setMode('veto')}>
          Veto
        </Button>
        <Button type="button" onClick={() => setMode('approve')}>
          Approve…
        </Button>
      </div>

      <FocusedOverlay
        open={mode === 'approve'}
        onClose={() => setMode(null)}
        ariaLabel="Approve proposal"
      >
        <div className="max-h-[80vh] w-[32rem] max-w-[90vw] overflow-y-auto bg-white p-6">
          <h3 className="mb-1 text-base font-medium text-slate-900">Approve change</h3>
          <p className="mb-4 text-sm text-slate-500">
            Edit any field before approving; your edits replace the proposed values. The change is
            written as a new version.
          </p>
          <DeltaFieldsEditor
            baseline={proposal.proposedDelta}
            values={values}
            onChange={(key, value) => setValues((prev) => ({ ...prev, [key]: value }))}
            idPrefix={`approve-${proposal.proposalId}`}
          />
          <div className="mt-6 flex justify-end gap-3">
            <Button type="button" variant="outline" onClick={() => setMode(null)} disabled={isPending}>
              Cancel
            </Button>
            <Button type="button" onClick={handleApprove} disabled={isPending} aria-disabled={isPending}>
              {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
              Approve &amp; write version
            </Button>
          </div>
        </div>
      </FocusedOverlay>

      <FocusedOverlay open={mode === 'veto'} onClose={() => setMode(null)} ariaLabel="Veto proposal">
        <div className="w-[24rem] max-w-[90vw] bg-white p-6">
          <h3 className="mb-2 text-base font-medium text-slate-900">Veto this proposal?</h3>
          <p className="mb-6 text-sm text-slate-500">
            The proposal is rejected and no version is written. This cannot be undone.
          </p>
          <div className="flex justify-end gap-3">
            <Button type="button" variant="outline" onClick={() => setMode(null)} disabled={isPending}>
              Cancel
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={handleVeto}
              disabled={isPending}
              aria-disabled={isPending}
            >
              {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
              Veto
            </Button>
          </div>
        </div>
      </FocusedOverlay>
    </div>
  )
}
