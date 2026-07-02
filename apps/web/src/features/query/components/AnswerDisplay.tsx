import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
  const headingRef = useRef<HTMLHeadingElement>(null)

  // Move focus to the answer heading on every mount so screen readers announce the new result.
  useEffect(() => {
    headingRef.current?.focus()
  }, [])

  return (
    <article aria-label={t('query.answer.articleAria')} className="space-y-6">
      <section aria-labelledby="answer-heading">
        <h2 id="answer-heading" ref={headingRef} tabIndex={-1} className="sr-only">
          {t('query.answer.heading')}
        </h2>
        <p className="whitespace-pre-wrap leading-relaxed text-foreground">{response.answer}</p>
      </section>

      {response.citations.length > 0 && (
        <CitationList citations={response.citations} campaignId={campaignId} />
      )}
    </article>
  )
}
