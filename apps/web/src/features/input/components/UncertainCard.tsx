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
    <article className="rounded-2xl border border-amber-200 bg-surface p-6 shadow-sm">
      <header className="mb-3">
        <Badge variant="outline" className="border-amber-200 bg-amber-50 text-amber-800">
          Requires Resolution
        </Badge>
      </header>
      <p className="text-sm text-foreground">
        Extracted mention: <strong>{card.extractedMention}</strong>
      </p>
      <p className="mb-4 text-sm text-foreground">
        Possible match: <strong>{card.candidateEntityName}</strong>
      </p>
      <RadioGroup
        value={resolution?.resolution ?? ''}
        onValueChange={handleChange}
        aria-label="Is this the same entity?"
      >
        <div className="flex items-center gap-2">
          <RadioGroupItem value="MATCH" id={matchId} />
          <Label htmlFor={matchId}>Same entity — link to {card.candidateEntityName}</Label>
        </div>
        <div className="flex items-center gap-2">
          <RadioGroupItem value="NEW" id={newId} />
          <Label htmlFor={newId}>Different entity — create new</Label>
        </div>
      </RadioGroup>
    </article>
  )
}
