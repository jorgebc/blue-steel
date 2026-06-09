import { CitationList } from './CitationList'
import type { QueryResponse } from '@/types/query'

interface Props {
  response: QueryResponse
  campaignId: string
}

/**
 * Renders a query answer and its citations. The answer is plain LLM text rendered with
 * `whitespace-pre-wrap` — never `dangerouslySetInnerHTML`. The citation section is omitted
 * entirely when no evidence was found (`citations` empty).
 */
export function AnswerDisplay({ response, campaignId }: Props) {
  return (
    <article aria-label="Query answer" className="space-y-6">
      <section aria-labelledby="answer-heading">
        <h2 id="answer-heading" className="sr-only">
          Answer
        </h2>
        <p className="whitespace-pre-wrap leading-relaxed text-slate-900">{response.answer}</p>
      </section>

      {response.citations.length > 0 && (
        <CitationList citations={response.citations} campaignId={campaignId} />
      )}
    </article>
  )
}
