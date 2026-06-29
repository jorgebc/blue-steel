import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { useCommitSession, useDiscardSession, useSessionDiff } from '@/api/sessions'
import { campaignKeys } from '@/api/campaigns'
import { useCampaignStore } from '@/store/campaignStore'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Button } from '@/components/ui/button'
import type { DiffCard, DiffPayload, ExistingDiffCard, NewDiffCard } from '@/types/session'
import { FocusedOverlay } from '@/components/domain/FocusedOverlay'
import { useDiffState } from './hooks/useDiffState'
import { buildCommitPayload } from './hooks/useCommitPayload'
import { AddEntityForm } from './components/AddEntityForm'
import { AddedEntityCard } from './components/AddedEntityCard'
import { CommitButton } from './components/CommitButton'
import { ConflictWarningCard } from './components/ConflictWarningCard'
import { DeltaCard } from './components/DeltaCard'
import { DiffCategorySection } from './components/DiffCategorySection'
import { DiscardConfirmOverlay } from './components/DiscardConfirmOverlay'
import { EditCardOverlay } from './components/EditCardOverlay'
import { NarrativeSummaryHeader } from './components/NarrativeSummaryHeader'
import { NewEntityCard } from './components/NewEntityCard'
import { UncertainCard } from './components/UncertainCard'

/** Editable cards (Delta/New); UNCERTAIN cards resolve inline, never via the edit overlay. */
type EditableCard = ExistingDiffCard | NewDiffCard

function DiffReviewSkeleton() {
  const { t } = useTranslation()
  return (
    <div role="status" aria-label={t('input.loadingDiff')} className="space-y-6">
      <div className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <div className="mb-2 h-6 w-1/3 rounded bg-muted animate-pulse" />
        <div className="h-4 w-3/4 rounded bg-muted animate-pulse" />
      </div>
      <div className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
        <div className="mb-3 h-5 w-1/4 rounded bg-muted animate-pulse" />
        <div className="mb-2 h-3 w-1/2 rounded bg-muted animate-pulse" />
        <div className="h-3 w-2/3 rounded bg-muted animate-pulse" />
      </div>
    </div>
  )
}

interface ContentProps {
  campaignId: string
  sessionId: string
  diff: DiffPayload
}

/** Inner content rendered only once the diff is loaded, so `useDiffState` gets a defined payload. */
function DiffReviewContent({ campaignId, sessionId, diff }: ContentProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const activeRole = useCampaignStore((s) => s.activeRole)
  const diffState = useDiffState(diff)
  const {
    decisions,
    uncertainResolutions,
    acknowledgedConflicts,
    addedEntities,
    setDecision,
    resolveUncertain,
    acknowledgeConflict,
    addEntity,
    removeAddedEntity,
    unresolvedUncertainCount,
    unacknowledgedConflictCount,
  } = diffState
  const [editingCard, setEditingCard] = useState<EditableCard | null>(null)
  const [addOpen, setAddOpen] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [commitError, setCommitError] = useState<string | null>(null)
  const [discardError, setDiscardError] = useState<string | null>(null)

  const { mutate: commit, isPending: isCommitting } = useCommitSession(campaignId, sessionId)
  const { mutate: discard, isPending: isDiscarding } = useDiscardSession(campaignId, sessionId)

  const categories: { key: string; title: string; cards: DiffCard[] }[] = [
    { key: 'actors', title: t('input.categoryActors'), cards: diff.actors },
    { key: 'spaces', title: t('input.categorySpaces'), cards: diff.spaces },
    { key: 'events', title: t('input.categoryEvents'), cards: diff.events },
    { key: 'relations', title: t('input.categoryRelations'), cards: diff.relations },
  ]
  // Backend rejects an empty cardDecisions (@NotEmpty) — only reachable with an all-UNCERTAIN diff.
  const hasCommittableCards = categories.some((c) =>
    c.cards.some((card) => card.cardType !== 'UNCERTAIN')
  )

  function renderCard(card: DiffCard) {
    switch (card.cardType) {
      case 'EXISTING':
        return (
          <DeltaCard
            key={card.cardId}
            card={card}
            decision={decisions.get(card.cardId) ?? { action: 'accept' }}
            onSetDecision={(d) => setDecision(card.cardId, d)}
            onEdit={() => setEditingCard(card)}
          />
        )
      case 'NEW':
        return (
          <NewEntityCard
            key={card.cardId}
            card={card}
            decision={decisions.get(card.cardId) ?? { action: 'accept' }}
            onSetDecision={(d) => setDecision(card.cardId, d)}
            onEdit={() => setEditingCard(card)}
          />
        )
      case 'UNCERTAIN':
        return (
          <UncertainCard
            key={card.cardId}
            card={card}
            resolution={uncertainResolutions.get(card.cardId)}
            onResolve={resolveUncertain}
          />
        )
    }
  }

  function handleCommit() {
    setCommitError(null)
    commit(buildCommitPayload(diff, diffState), {
      onSuccess() {
        // Refresh the campaign home (it reflects the new world state) and leave the
        // review — the session is no longer DRAFT, so GET .../diff would now 404.
        queryClient.invalidateQueries({ queryKey: campaignKeys.detail(campaignId) })
        navigate(`/campaigns/${campaignId}`, { replace: true })
      },
      onError(err) {
        // The disabled button should have prevented any 422 — treat it as a UI bug.
        console.error('Commit failed', err)
        setCommitError(t('input.commitErrorGeneric'))
      },
    })
  }

  function handleDiscardConfirm() {
    setDiscardError(null)
    discard(undefined, {
      onSuccess() {
        navigate(`/campaigns/${campaignId}/sessions/new`)
      },
      onError(err) {
        console.error('Discard failed', err)
        setDiscardOpen(false)
        setDiscardError(t('input.discardError'))
      },
    })
  }

  return (
    <div className="space-y-6">
      <NarrativeSummaryHeader summary={diff.narrativeSummaryHeader} />

      {diff.detectedConflicts.length > 0 && (
        <section aria-label={t('input.detectedConflicts')} className="space-y-3">
          {diff.detectedConflicts.map((conflict) => (
            <ConflictWarningCard
              key={conflict.conflictId}
              conflict={conflict}
              acknowledged={acknowledgedConflicts.has(conflict.conflictId)}
              onAcknowledge={acknowledgeConflict}
            />
          ))}
        </section>
      )}

      {categories
        .filter((c) => c.cards.length > 0)
        .map((c) => (
          <DiffCategorySection key={c.key} title={c.title} count={c.cards.length}>
            {c.cards.map(renderCard)}
          </DiffCategorySection>
        ))}

      {addedEntities.size > 0 && (
        <DiffCategorySection title={t('input.categoryAdded')} count={addedEntities.size}>
          {[...addedEntities].map(([id, entity]) => (
            <AddedEntityCard key={id} entity={entity} onRemove={() => removeAddedEntity(id)} />
          ))}
        </DiffCategorySection>
      )}

      <div>
        <Button type="button" variant="outline" onClick={() => setAddOpen(true)}>
          {t('input.addEntity')}
        </Button>
      </div>

      <FocusedOverlay
        open={addOpen}
        onClose={() => setAddOpen(false)}
        ariaLabel={t('input.addEntity')}
      >
        <AddEntityForm
          onAdd={(entity) => {
            addEntity(entity)
            setAddOpen(false)
          }}
          onCancel={() => setAddOpen(false)}
        />
      </FocusedOverlay>

      {editingCard && (
        <EditCardOverlay
          key={editingCard.cardId}
          card={editingCard}
          open
          onClose={() => {
            setDecision(editingCard.cardId, { action: 'accept' })
            setEditingCard(null)
          }}
          onSave={(editedFields) => {
            setDecision(editingCard.cardId, { action: 'edit', editedFields })
            setEditingCard(null)
          }}
        />
      )}

      {commitError && (
        <InlineBanner
          variant="error"
          message={commitError}
          onDismiss={() => setCommitError(null)}
        />
      )}
      {discardError && (
        <InlineBanner
          variant="error"
          message={discardError}
          onDismiss={() => setDiscardError(null)}
        />
      )}

      <div className="flex items-center justify-between gap-3">
        <CommitButton
          unresolvedUncertainCount={unresolvedUncertainCount}
          unacknowledgedConflictCount={unacknowledgedConflictCount}
          isPending={isCommitting}
          hasCommittableCards={hasCommittableCards}
          onCommit={handleCommit}
        />
        {activeRole === 'gm' && (
          <Button
            type="button"
            variant="outline"
            onClick={() => setDiscardOpen(true)}
            className="border-red-200 text-red-600 hover:bg-red-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950/40"
          >
            {t('input.discardDraft')}
          </Button>
        )}
      </div>

      <DiscardConfirmOverlay
        open={discardOpen}
        isPending={isDiscarding}
        onConfirm={handleDiscardConfirm}
        onClose={() => setDiscardOpen(false)}
      />
    </div>
  )
}

/**
 * Diff review screen: fetches the draft diff, shows a skeleton while loading, an
 * error banner on failure/404, and otherwise the narrative header, conflicts, and
 * per-category decision cards with the commit guard, commit wiring, and GM discard.
 */
export function DiffReviewPage() {
  const { t } = useTranslation()
  const { campaignId, sessionId } = useParams<{ campaignId: string; sessionId: string }>()
  const { data: diff, isLoading, isError } = useSessionDiff(campaignId ?? '', sessionId ?? '')
  const [errorDismissed, setErrorDismissed] = useState(false)

  return (
    <main className="mx-auto max-w-3xl p-8">
      {isLoading && <DiffReviewSkeleton />}
      {isError && !errorDismissed && (
        <InlineBanner
          variant="error"
          message={t('input.loadError')}
          onDismiss={() => setErrorDismissed(true)}
        />
      )}
      {diff && (
        <DiffReviewContent campaignId={campaignId ?? ''} sessionId={sessionId ?? ''} diff={diff} />
      )}
    </main>
  )
}
