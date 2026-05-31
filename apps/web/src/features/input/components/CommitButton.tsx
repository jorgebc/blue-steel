import { Loader2 } from 'lucide-react'
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
  const pending = unresolvedUncertainCount + unacknowledgedConflictCount
  const disabled = pending > 0 || isPending || !hasCommittableCards

  const note = !hasCommittableCards
    ? 'No entities to commit.'
    : pending > 0
      ? `${pending} item${pending !== 1 ? 's' : ''} require your decision`
      : null

  const ariaLabel = !hasCommittableCards
    ? 'Commit to world state — no entities to commit'
    : pending > 0
      ? `Commit to world state — ${pending} item${pending !== 1 ? 's' : ''} require your decision first`
      : 'Commit to world state'

  return (
    <div className="space-y-2">
      {note && <p className="text-sm text-slate-500">{note}</p>}
      <Button
        type="button"
        onClick={onCommit}
        disabled={disabled}
        aria-disabled={disabled}
        aria-label={ariaLabel}
      >
        {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
        Commit to world state
      </Button>
    </div>
  )
}
