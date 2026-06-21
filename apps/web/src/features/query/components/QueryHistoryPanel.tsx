import { useState } from 'react'
import { useQueryHistory } from '@/api/queries'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { AnswerDisplay } from './AnswerDisplay'
import { QueryHistorySkeleton } from './QueryHistorySkeleton'
import type { QueryHistoryEntry } from '@/types/query'

interface Props {
  campaignId: string
  /** Bumped by the parent on each successful submit; jumps the panel back to the newest entry. */
  refreshSignal?: number
}

const PAGE_SIZE = 20
const LOAD_ERROR_MESSAGE = 'Could not load the question history. Please try again.'

/** Renders a logged Q&A entry's timestamp as a readable local string at the render boundary. */
function formatAskedAt(createdAt: string): string {
  return new Date(createdAt).toLocaleString()
}

/**
 * Query Mode history panel: lists the campaign's past Q&As newest-first (F6.4, D-058) and, on
 * selection, shows that entry's grounded answer + citations via the shared {@link AnswerDisplay}.
 * Genuine server state (offset-paginated, D-055) — distinct from the stateless live-answer flow,
 * which stays untouched. Loading uses a DTO-derived skeleton, never a spinner (D-086); load
 * failures surface through `InlineBanner`, never a toast (D-083).
 */
export function QueryHistoryPanel({ campaignId, refreshSignal }: Props) {
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<QueryHistoryEntry | null>(null)
  const [errorDismissed, setErrorDismissed] = useState(false)
  const [seenRefreshSignal, setSeenRefreshSignal] = useState(refreshSignal)

  // A new query (signalled by the parent) lands on page 0 newest-first — return there so it shows.
  // Resetting during render (not in an effect) is React's recommended prop-change-reset pattern.
  if (refreshSignal !== seenRefreshSignal) {
    setSeenRefreshSignal(refreshSignal)
    setPage(0)
    setSelected(null)
  }

  const { data, isLoading, isError } = useQueryHistory(campaignId, page)

  function goToPage(next: number) {
    setSelected(null)
    setPage(next)
  }

  if (isLoading) return <QueryHistorySkeleton />

  if (isError && !errorDismissed) {
    return (
      <InlineBanner
        variant="error"
        message={LOAD_ERROR_MESSAGE}
        onDismiss={() => setErrorDismissed(true)}
      />
    )
  }

  const items = data?.items ?? []
  const totalCount = data?.totalCount ?? 0
  const hasPrev = page > 0
  const hasNext = (page + 1) * PAGE_SIZE < totalCount

  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">No questions have been asked yet.</p>
  }

  return (
    <div className="space-y-4">
      <ul aria-label="Past questions" className="space-y-2">
        {items.map((entry) => {
          const isSelected = selected?.id === entry.id
          return (
            <li key={entry.id}>
              <button
                type="button"
                onClick={() => setSelected(entry)}
                aria-current={isSelected ? 'true' : undefined}
                className={[
                  'w-full rounded-lg border p-4 text-left',
                  isSelected
                    ? 'border-accent bg-accent-subtle'
                    : 'border-border bg-surface hover:border-border-strong',
                ].join(' ')}
              >
                <span className="block text-sm font-medium text-foreground">{entry.question}</span>
                <span className="mt-1 block text-xs text-muted-foreground">
                  {formatAskedAt(entry.createdAt)}
                </span>
              </button>
            </li>
          )
        })}
      </ul>

      {(hasPrev || hasNext) && (
        <nav aria-label="History pages" className="flex items-center justify-between">
          <button
            type="button"
            onClick={() => goToPage(page - 1)}
            disabled={!hasPrev}
            className="rounded-lg border border-border px-3 py-1 text-sm text-foreground disabled:opacity-50"
          >
            Previous
          </button>
          <button
            type="button"
            onClick={() => goToPage(page + 1)}
            disabled={!hasNext}
            className="rounded-lg border border-border px-3 py-1 text-sm text-foreground disabled:opacity-50"
          >
            Next
          </button>
        </nav>
      )}

      {selected && (
        <AnswerDisplay
          key={selected.id}
          response={{ answer: selected.answer, citations: selected.citations }}
          campaignId={campaignId}
        />
      )}
    </div>
  )
}
