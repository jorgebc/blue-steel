import { useState } from 'react'
import { useQueryHistory } from '@/api/queries'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { AnswerDisplay } from './AnswerDisplay'
import { QueryHistorySkeleton } from './QueryHistorySkeleton'
import type { QueryHistoryEntry } from '@/types/query'

interface Props {
  campaignId: string
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
export function QueryHistoryPanel({ campaignId }: Props) {
  const [page, setPage] = useState(0)
  const [selected, setSelected] = useState<QueryHistoryEntry | null>(null)
  const [errorDismissed, setErrorDismissed] = useState(false)
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
    return <p className="text-sm text-slate-500">No questions have been asked yet.</p>
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
                    ? 'border-blue-500 bg-blue-50'
                    : 'border-slate-200 bg-white hover:border-slate-300',
                ].join(' ')}
              >
                <span className="block text-sm font-medium text-slate-900">{entry.question}</span>
                <span className="mt-1 block text-xs text-slate-500">
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
            className="rounded-lg border border-slate-200 px-3 py-1 text-sm text-slate-700 disabled:opacity-50"
          >
            Previous
          </button>
          <button
            type="button"
            onClick={() => goToPage(page + 1)}
            disabled={!hasNext}
            className="rounded-lg border border-slate-200 px-3 py-1 text-sm text-slate-700 disabled:opacity-50"
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
