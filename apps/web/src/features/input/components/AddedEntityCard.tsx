import { Trash2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
  const fields = Object.entries(entity.fields)
  return (
    <article className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
      <header className="mb-3 flex items-center justify-between gap-3">
        <h3 className="text-base font-medium text-foreground">{entity.name}</h3>
        <div className="flex items-center gap-2">
          <Badge variant="outline">{entity.entityType}</Badge>
          <Badge>{t('input.added')}</Badge>
        </div>
      </header>
      <dl className="mb-4 space-y-1 text-sm">
        {fields.length === 0 ? (
          <p className="text-muted-foreground">{t('input.noProfileFields')}</p>
        ) : (
          fields.map(([key, value]) => (
            <div key={key} className="flex gap-2">
              <dt className="text-muted-foreground">{key}</dt>
              <dd className="text-foreground">{formatFieldValue(value)}</dd>
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
        {t('common.remove')}
      </Button>
    </article>
  )
}
