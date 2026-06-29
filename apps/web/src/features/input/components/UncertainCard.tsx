import { useTranslation } from 'react-i18next'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import type { UncertainDiffCard } from '@/types/session'
import type { UncertainResolution } from '../hooks/useDiffState'

interface Props {
  card: UncertainDiffCard
  resolution: UncertainResolution | undefined
  onResolve: (r: UncertainResolution) => void
}

/**
 * Forces the binary resolution of an UNCERTAIN card — link to the candidate
 * (MATCH) or create a new entity (NEW). No defer/skip option (D-042).
 */
export function UncertainCard({ card, resolution, onResolve }: Props) {
  const { t } = useTranslation()
  const matchId = `${card.cardId}-match`
  const newId = `${card.cardId}-new`

  function handleChange(value: string) {
    if (value === 'MATCH') {
      onResolve({
        cardId: card.cardId,
        resolution: 'MATCH',
        matchedEntityId: card.candidateEntityId,
      })
    } else {
      onResolve({ cardId: card.cardId, resolution: 'NEW', matchedEntityId: null })
    }
  }

  return (
    <article className="rounded-2xl border border-amber-200 bg-surface p-6 shadow-sm dark:border-amber-900">
      <header className="mb-3">
        <Badge
          variant="outline"
          className="border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200"
        >
          {t('input.requiresResolution')}
        </Badge>
      </header>
      <p className="text-sm text-foreground">
        {t('input.extractedMention')} <strong>{card.extractedMention}</strong>
      </p>
      <p className="mb-4 text-sm text-foreground">
        {t('input.possibleMatch')} <strong>{card.candidateEntityName}</strong>
      </p>
      <RadioGroup
        value={resolution?.resolution ?? ''}
        onValueChange={handleChange}
        aria-label={t('input.sameEntityQuestion')}
      >
        <div className="flex items-center gap-2">
          <RadioGroupItem value="MATCH" id={matchId} />
          <Label htmlFor={matchId}>
            {t('input.sameEntity', { name: card.candidateEntityName })}
          </Label>
        </div>
        <div className="flex items-center gap-2">
          <RadioGroupItem value="NEW" id={newId} />
          <Label htmlFor={newId}>{t('input.differentEntity')}</Label>
        </div>
      </RadioGroup>
    </article>
  )
}
