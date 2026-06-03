import { Badge } from '@/components/ui/badge'
import type { NewDiffCard } from '@/types/session'
import type { CardDecision } from '../hooks/useDiffState'
import { CardDecisionActions } from './CardDecisionActions'
import { formatFieldValue } from './fieldFormat'

interface Props {
  card: NewDiffCard
  decision: CardDecision
  onSetDecision: (d: CardDecision) => void
  onEdit: () => void
}

/** Entity appearing for the first time — shows the full extracted profile (D-007). */
export function NewEntityCard({ card, decision, onSetDecision, onEdit }: Props) {
  const edited = decision.action === 'edit'
  const fields = Object.entries(edited ? decision.editedFields : card.fullProfile)
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <header className="mb-3 flex items-center justify-between gap-3">
        <h3 className="text-base font-medium text-slate-900">{card.name}</h3>
        <div className="flex items-center gap-2">
          {edited && <Badge variant="outline">Edited</Badge>}
          <Badge>New</Badge>
        </div>
      </header>
      <dl className="mb-4 space-y-1 text-sm">
        {fields.length === 0 ? (
          <p className="text-slate-500">No profile fields.</p>
        ) : (
          fields.map(([key, value]) => (
            <div key={key} className="flex gap-2">
              <dt className="text-slate-500">{key}</dt>
              <dd className="text-slate-900">{formatFieldValue(value)}</dd>
            </div>
          ))
        )}
      </dl>
      <CardDecisionActions decision={decision} onSetDecision={onSetDecision} onEdit={onEdit} />
    </article>
  )
}
