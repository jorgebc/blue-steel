import { useState } from 'react'
import { ApiClientError } from '@/api/client'
import { useSubmitQuery } from '@/api/queries'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { useCampaignStore } from '@/store/campaignStore'
import { QuestionForm } from './components/QuestionForm'
import { QueryAnswerSkeleton } from './components/QueryAnswerSkeleton'
import { AnswerDisplay } from './components/AnswerDisplay'
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
 * Stateless by design — the answer lives in local `useState` and is discarded on navigation;
 * there is no Q&A history (D-058). Feedback (504 timeout, generic errors) uses `InlineBanner`,
 * never a toast (D-083); the loading state is a skeleton, never a spinner (D-086).
 */
export function QueryPage() {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  const [answer, setAnswer] = useState<QueryResponse | null>(null)
  const [banner, setBanner] = useState<Banner | null>(null)
  const { mutate, isPending } = useSubmitQuery(campaignId ?? '')

  function handleSubmit(question: string) {
    // Clear prior answer + feedback so stale state never bleeds into the new query.
    setAnswer(null)
    setBanner(null)
    mutate(question, {
      onSuccess: (data) => setAnswer(data),
      onError: (err) => setBanner(toBanner(err)),
    })
  }

  return (
    <section aria-labelledby="query-mode-heading" className="mx-auto max-w-3xl space-y-6 p-8">
      <h1 id="query-mode-heading" className="text-2xl font-semibold text-slate-900">
        Ask the World
      </h1>

      <QuestionForm onSubmit={handleSubmit} isPending={isPending} />

      {banner && (
        <InlineBanner
          variant={banner.variant}
          message={banner.message}
          onDismiss={() => setBanner(null)}
        />
      )}

      {isPending && <QueryAnswerSkeleton />}

      {!isPending && answer && <AnswerDisplay response={answer} campaignId={campaignId ?? ''} />}
    </section>
  )
}
