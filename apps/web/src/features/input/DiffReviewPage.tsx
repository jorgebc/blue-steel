import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useSessionDiff } from '@/api/sessions'
import { InlineBanner } from '@/components/domain/InlineBanner'
import type { DiffCard, DiffPayload, ExistingDiffCard, NewDiffCard } from '@/types/session'
import { useDiffState } from './hooks/useDiffState'
import { CommitButton } from './components/CommitButton'
import { ConflictWarningCard } from './components/ConflictWarningCard'
import { DeltaCard } from './components/DeltaCard'
import { DiffCategorySection } from './components/DiffCategorySection'
import { EditCardOverlay } from './components/EditCardOverlay'
import { NarrativeSummaryHeader } from './components/NarrativeSummaryHeader'
import { NewEntityCard } from './components/NewEntityCard'
import { UncertainCard } from './components/UncertainCard'

/** Editable cards (Delta/New); UNCERTAIN cards resolve inline, never via the edit overlay. */
type EditableCard = ExistingDiffCard | NewDiffCard

function DiffReviewSkeleton() {
  return (
    <div role="status" aria-label="Loading session diff" className="space-y-6">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-2 h-6 w-1/3 rounded bg-slate-200 animate-pulse" />
        <div className="h-4 w-3/4 rounded bg-slate-200 animate-pulse" />
      </div>
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="mb-3 h-5 w-1/4 rounded bg-slate-200 animate-pulse" />
        <div className="mb-2 h-3 w-1/2 rounded bg-slate-200 animate-pulse" />
        <div className="h-3 w-2/3 rounded bg-slate-200 animate-pulse" />
      </div>
    </div>
  )
}

interface ContentProps {
  diff: DiffPayload
}

/** Inner content rendered only once the diff is loaded, so `useDiffState` gets a defined payload. */
function DiffReviewContent({ diff }: ContentProps) {
  const {
    decisions,
    uncertainResolutions,
    acknowledgedConflicts,
    setDecision,
    resolveUncertain,
    acknowledgeConflict,
    unresolvedUncertainCount,
    unacknowledgedConflictCount,
  } = useDiffState(diff)
  const [editingCard, setEditingCard] = useState<EditableCard | null>(null)

  const categories: { title: string; cards: DiffCard[] }[] = [
    { title: 'Actors', cards: diff.actors },
    { title: 'Spaces', cards: diff.spaces },
    { title: 'Events', cards: diff.events },
    { title: 'Relations', cards: diff.relations },
  ]

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
    /* The commit mutation is wired in F2.11 — this is a placeholder. */
  }

  return (
    <div className="space-y-6">
      <NarrativeSummaryHeader summary={diff.narrativeSummaryHeader} />

      {diff.detectedConflicts.length > 0 && (
        <section aria-label="Detected conflicts" className="space-y-3">
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
          <DiffCategorySection key={c.title} title={c.title} count={c.cards.length}>
            {c.cards.map(renderCard)}
          </DiffCategorySection>
        ))}

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

      <CommitButton
        unresolvedUncertainCount={unresolvedUncertainCount}
        unacknowledgedConflictCount={unacknowledgedConflictCount}
        isPending={false}
        onCommit={handleCommit}
      />
    </div>
  )
}

/**
 * Diff review screen: fetches the draft diff, shows a skeleton while loading, an
 * error banner on failure/404, and otherwise the narrative header, conflicts, and
 * per-category decision cards with the commit guard. The commit mutation itself is
 * F2.11 (here `onCommit` is a placeholder).
 */
export function DiffReviewPage() {
  const { campaignId, sessionId } = useParams<{ campaignId: string; sessionId: string }>()
  const { data: diff, isLoading, isError } = useSessionDiff(campaignId ?? '', sessionId ?? '')
  const [errorDismissed, setErrorDismissed] = useState(false)

  return (
    <main className="mx-auto max-w-3xl p-8">
      {isLoading && <DiffReviewSkeleton />}
      {isError && !errorDismissed && (
        <InlineBanner
          variant="error"
          message="We couldn't load this session's review. It may no longer be a draft."
          onDismiss={() => setErrorDismissed(true)}
        />
      )}
      {diff && <DiffReviewContent diff={diff} />}
    </main>
  )
}
