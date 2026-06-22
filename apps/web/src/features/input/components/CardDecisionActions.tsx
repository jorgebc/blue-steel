import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import type { CardDecision } from '../hooks/useDiffState'

interface Props {
  decision: CardDecision
  onSetDecision: (d: CardDecision) => void
  onEdit: () => void
}

/** Accept / Edit / Delete action row shared by the Delta and New entity cards (no "add" — D-053). */
export function CardDecisionActions({ decision, onSetDecision, onEdit }: Props) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-wrap gap-2">
      <Button
        type="button"
        size="sm"
        variant={decision.action === 'accept' ? 'default' : 'outline'}
        aria-pressed={decision.action === 'accept'}
        onClick={() => onSetDecision({ action: 'accept' })}
      >
        {t('common.accept')}
      </Button>
      <Button
        type="button"
        size="sm"
        variant={decision.action === 'edit' ? 'default' : 'outline'}
        onClick={onEdit}
      >
        {t('common.edit')}
      </Button>
      <Button
        type="button"
        size="sm"
        variant={decision.action === 'delete' ? 'destructive' : 'outline'}
        aria-pressed={decision.action === 'delete'}
        onClick={() => onSetDecision({ action: 'delete' })}
      >
        {t('common.delete')}
      </Button>
    </div>
  )
}
