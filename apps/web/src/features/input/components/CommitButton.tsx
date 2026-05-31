import { Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface Props {
  unresolvedUncertainCount: number
  unacknowledgedConflictCount: number
  isPending: boolean
  onCommit: () => void
}

/**
 * The primary D-042 guard: disabled while any UNCERTAIN card is unresolved, any
 * conflict is unacknowledged, or a commit is in flight. The backend `422` is only
 * defence in depth.
 */
export function CommitButton({
  unresolvedUncertainCount,
  unacknowledgedConflictCount,
  isPending,
  onCommit,
}: Props) {
  const pending = unresolvedUncertainCount + unacknowledgedConflictCount
  const disabled = pending > 0 || isPending

  return (
    <div className="space-y-2">
      {pending > 0 && (
        <p className="text-sm text-slate-500">
          {pending} item{pending !== 1 ? 's' : ''} require your decision
        </p>
      )}
      <Button
        type="button"
        onClick={onCommit}
        disabled={disabled}
        aria-disabled={disabled}
        aria-label={
          pending > 0
            ? `Commit to world state — ${pending} item${pending !== 1 ? 's' : ''} require your decision first`
            : 'Commit to world state'
        }
      >
        {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
        Commit to world state
      </Button>
    </div>
  )
}
