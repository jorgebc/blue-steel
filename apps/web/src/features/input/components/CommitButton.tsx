import { Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'

interface Props {
  unresolvedUncertainCount: number
  unacknowledgedConflictCount: number
  isPending: boolean
  /** False when the diff has no committable (non-UNCERTAIN) cards — commit would be rejected. */
  hasCommittableCards?: boolean
  onCommit: () => void
}

/**
 * The primary D-042 guard: disabled while any UNCERTAIN card is unresolved, any
 * conflict is unacknowledged, a commit is in flight, or there is nothing to commit.
 * The backend `422` is only defence in depth.
 */
export function CommitButton({
  unresolvedUncertainCount,
  unacknowledgedConflictCount,
  isPending,
  hasCommittableCards = true,
  onCommit,
}: Props) {
  const { t } = useTranslation()
  const pending = unresolvedUncertainCount + unacknowledgedConflictCount
  const disabled = pending > 0 || isPending || !hasCommittableCards

  const note = !hasCommittableCards
    ? t('input.commitNoEntities')
    : pending > 0
      ? t('input.commitNote', { count: pending })
      : null

  const ariaLabel = !hasCommittableCards
    ? t('input.commitAriaNoEntities')
    : pending > 0
      ? t('input.commitAriaPending', { count: pending })
      : t('input.commitLabel')

  return (
    <div className="space-y-2">
      {note && <p className="text-sm text-muted-foreground">{note}</p>}
      <Button
        type="button"
        onClick={onCommit}
        disabled={disabled}
        aria-disabled={disabled}
        aria-label={ariaLabel}
      >
        {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
        {t('input.commitLabel')}
      </Button>
    </div>
  )
}
