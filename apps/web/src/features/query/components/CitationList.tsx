import { Link } from 'react-router-dom'
import type { Citation } from '@/types/query'

interface Props {
  citations: Citation[]
  campaignId: string
}

/**
 * Renders the answer's grounding citations as navigable links to each cited session's detail
 * page (D-003). The target route is registered in F3.4.8; the page itself is the read-only
 * {@link SessionDetailPage}.
 */
export function CitationList({ citations, campaignId }: Props) {
  return (
    <aside aria-labelledby="citations-heading" className="space-y-2">
      <h3 id="citations-heading" className="text-sm font-semibold text-slate-700">
        Sources
      </h3>
      <ol className="space-y-2">
        {citations.map((c) => (
          <li key={`${c.sessionId}-${c.snippet}`} className="text-sm text-slate-700">
            <Link
              to={`/campaigns/${campaignId}/sessions/${c.sessionId}`}
              aria-label={`Session ${c.sequenceNumber}: ${c.snippet}`}
              className="font-medium text-blue-500 underline-offset-4 hover:underline"
            >
              Session {c.sequenceNumber}
            </Link>
            {' — '}
            <span>{c.snippet}</span>
          </li>
        ))}
      </ol>
    </aside>
  )
}
