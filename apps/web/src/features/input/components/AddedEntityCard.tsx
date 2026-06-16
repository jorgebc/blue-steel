import { Trash2 } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import type { AddedEntityPayload } from '@/types/session'
import { formatFieldValue } from './fieldFormat'

interface Props {
  entity: AddedEntityPayload
  onRemove: () => void
}

/** A reviewer-added entity rendered as its own diff card, with a remove affordance (F6.2, D-053). */
export function AddedEntityCard({ entity, onRemove }: Props) {
  const fields = Object.entries(entity.fields)
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <header className="mb-3 flex items-center justify-between gap-3">
        <h3 className="text-base font-medium text-slate-900">{entity.name}</h3>
        <div className="flex items-center gap-2">
          <Badge variant="outline">{entity.entityType}</Badge>
          <Badge>Added</Badge>
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
      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={onRemove}
        className="border-red-200 text-red-600 hover:bg-red-50"
      >
        <Trash2 className="mr-2 h-4 w-4" aria-hidden />
        Remove
      </Button>
    </article>
  )
}
