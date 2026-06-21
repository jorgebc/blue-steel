import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { ApiClientError } from '@/api/client'
import { queryHistoryKeyPrefix, queryUsageKey, useQueryUsage, useSubmitQuery } from '@/api/queries'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { useCampaignStore } from '@/store/campaignStore'
import { QuestionForm } from './components/QuestionForm'
import { QueryAnswerSkeleton } from './components/QueryAnswerSkeleton'
import { QueryUsageNotice } from './components/QueryUsageNotice'
import { AnswerDisplay } from './components/AnswerDisplay'
import { QueryHistoryPanel } from './components/QueryHistoryPanel'
import type { QueryResponse } from '@/types/query'

interface Banner {
  variant: 'warning' | 'error'
  message: string
}

const TIMEOUT_MESSAGE =
  'That query took too long to answer. Try rephrasing it or narrowing the scope, then ask again.'
const RATE_LIMIT_MESSAGE =
  "You're sending questions too quickly. Wait a few seconds, then ask again."
const COST_CAP_MESSAGE =
  'The daily question limit for this service has been reached. Please try again tomorrow.'
const GENERIC_MESSAGE = 'Something went wrong answering that question. Please try again.'

/**
 * Maps a failed query to a banner. The synchronous-deadline (504), rate-limit (429), and daily
 * cost-cap (503) guards (D-052, D-096) get tailored, retry-oriented copy; any other coded backend
 * error surfaces its own envelope message; anything else falls back to a generic message.
 */
function toBanner(err: unknown): Banner {
  if (err instanceof ApiClientError) {
    if (err.status === 504) return { variant: 'warning', message: TIMEOUT_MESSAGE }
    if (err.status === 429) return { variant: 'warning', message: RATE_LIMIT_MESSAGE }
    if (err.status === 503) return { variant: 'warning', message: COST_CAP_MESSAGE }
    const backendMessage = err.errors[0]?.message
    if (backendMessage) return { variant: 'error', message: backendMessage }
  }
  return { variant: 'error', message: GENERIC_MESSAGE }
}

/**
 * Query Mode screen: ask a question, wait synchronously, render the grounded answer (D-052).
 * The live answer lives in local `useState` and is discarded on navigation; the persisted Q&A log
 * is surfaced separately by the history panel below (F6.4/F6.5, D-058 resolved in v2). Feedback
 * (504 timeout, generic errors) uses `InlineBanner`, never a toast (D-083); the loading state is a
 * skeleton, never a spinner (D-086).
 */
export function QueryPage() {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  const [answer, setAnswer] = useState<QueryResponse | null>(null)
  const [banner, setBanner] = useState<Banner | null>(null)
  // Bumped on each successful submit so the history panel jumps back to its newest (page 0) entry.
  const [historyRefreshSignal, setHistoryRefreshSignal] = useState(0)
  const queryClient = useQueryClient()
  const { mutate, isPending } = useSubmitQuery(campaignId ?? '')
  const { data: usage } = useQueryUsage(campaignId ?? '')

  function handleSubmit(question: string) {
    // Clear prior answer + feedback so stale state never bleeds into the new query.
    setAnswer(null)
    setBanner(null)
    mutate(question, {
      onSuccess: (data) => {
        setAnswer(data)
        // The query was persisted server-side — refetch the history so it shows without a reload.
        queryClient.invalidateQueries({ queryKey: queryHistoryKeyPrefix(campaignId ?? '') })
        setHistoryRefreshSignal((n) => n + 1)
      },
      onError: (err) => setBanner(toBanner(err)),
      // A submission moves the shared budget and the per-minute window — refresh the figure.
      onSettled: () => queryClient.invalidateQueries({ queryKey: queryUsageKey(campaignId ?? '') }),
    })
  }

  return (
    <section aria-labelledby="query-mode-heading" className="mx-auto max-w-3xl space-y-6 p-8">
      <h1 id="query-mode-heading" className="text-2xl font-semibold text-foreground">
        Ask the World
      </h1>

      <QueryUsageNotice usage={usage} />

      <QuestionForm onSubmit={handleSubmit} isPending={isPending} />

      {banner && (
        <InlineBanner
          variant={banner.variant}
          message={banner.message}
          onDismiss={() => setBanner(null)}
        />
      )}

      {isPending && <QueryAnswerSkeleton />}

      {!isPending && !answer && !banner && (
        <p className="text-sm text-muted-foreground">
          Submit a question to see the answer and its sources here.
        </p>
      )}

      {!isPending && answer && <AnswerDisplay response={answer} campaignId={campaignId ?? ''} />}

      <section
        aria-labelledby="query-history-heading"
        className="space-y-4 border-t border-border pt-6"
      >
        <h2 id="query-history-heading" className="text-lg font-semibold text-foreground">
          Question history
        </h2>
        <QueryHistoryPanel campaignId={campaignId ?? ''} refreshSignal={historyRefreshSignal} />
      </section>
    </section>
  )
}
