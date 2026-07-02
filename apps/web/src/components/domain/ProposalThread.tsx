import { useState } from 'react'
import { useTranslation } from 'react-i18next'
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
          <dt className="w-32 shrink-0 font-medium text-muted-foreground">{key}</dt>
          <dd className="text-foreground">{String(value)}</dd>
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
  const { t } = useTranslation()
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  const activeRole = useCampaignStore((s) => s.activeRole)
  const currentUserId = useAuthStore((s) => s.currentUser?.id)

  const { data, isLoading, isError } = useProposalsForTarget(campaignId ?? '', targetType, targetId)
  const coSign = useCoSignProposal(campaignId ?? '')

  const [feedback, setFeedback] = useState<Feedback>(null)

  const proposals = data?.proposals ?? []

  function canCoSign(proposal: Proposal): boolean {
    // The GM decides proposals and cannot co-sign them (D-017); the author cannot co-sign their own.
    return proposal.status === 'OPEN' && activeRole !== 'gm' && currentUserId !== proposal.ownerId
  }

  function handleCoSign(proposalId: string) {
    setFeedback(null)
    coSign.mutate(proposalId, {
      onSuccess: () =>
        setFeedback({ variant: 'success', message: t('proposals.thread.coSignSuccess') }),
      onError: (err) => {
        const message =
          err instanceof ApiClientError
            ? (err.errors[0]?.message ?? t('proposals.thread.coSignError'))
            : t('proposals.thread.coSignError')
        setFeedback({ variant: 'error', message })
      },
    })
  }

  return (
    <section
      aria-label={t('proposals.thread.sectionAria')}
      className="mt-8 border-t-2 border-dashed border-blue-300 pt-6 dark:border-blue-800"
    >
      <header className="mb-4">
        <h2 className="text-sm font-semibold text-blue-900 dark:text-blue-200">
          {t('proposals.thread.heading')}
        </h2>
        <p className="text-xs text-blue-700 dark:text-blue-300">{t('proposals.thread.subtitle')}</p>
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
          message={t('proposals.thread.loadError')}
          onDismiss={() => undefined}
        />
      )}

      {!isLoading && !isError && (
        <div className="space-y-3">
          {proposals.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              {targetType === 'ACTOR'
                ? t('proposals.thread.emptyActor')
                : t('proposals.thread.emptySpace')}
            </p>
          ) : (
            proposals.map((proposal) => (
              <div
                key={proposal.proposalId}
                className="rounded-xl border border-border bg-surface p-4 shadow-sm"
              >
                <div className="mb-3 flex items-center justify-between gap-3">
                  <ProposalStatusBadge status={proposal.status} />
                  <span className="text-xs text-muted-foreground">
                    {t('proposals.thread.expires', {
                      date: new Date(proposal.expiresAt).toLocaleDateString(),
                    })}
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
                      {t('proposals.thread.coSign')}
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
