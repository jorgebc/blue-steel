import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import type { ConflictCard } from '@/types/session'

interface Props {
  conflict: ConflictCard
  acknowledged: boolean
  onAcknowledge: (conflictId: string) => void
}

/**
 * Surfaces a detected contradiction. Non-blocking, but the user must acknowledge
 * it before commit (D-033).
 */
export function ConflictWarningCard({ conflict, acknowledged, onAcknowledge }: Props) {
  const ackId = `${conflict.conflictId}-ack`
  return (
    <div
      role="alert"
      className="rounded-2xl border border-amber-200 bg-amber-50 p-6 dark:border-amber-900 dark:bg-amber-950"
    >
      <header className="mb-3">
        <Badge
          variant="outline"
          className="border-amber-300 bg-amber-100 text-amber-800 dark:border-amber-800 dark:bg-amber-900 dark:text-amber-200"
        >
          Conflict
        </Badge>
      </header>
      <p className="mb-3 text-sm text-amber-900 dark:text-amber-100">{conflict.description}</p>
      <dl className="mb-4 space-y-1 text-sm">
        <div className="flex gap-2">
          <dt className="text-amber-700 dark:text-amber-300">This session</dt>
          <dd className="text-amber-900 dark:text-amber-100">{conflict.extractedFact}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="text-amber-700 dark:text-amber-300">World state</dt>
          <dd className="text-amber-900 dark:text-amber-100">{conflict.existingFact}</dd>
        </div>
      </dl>
      <div className="flex items-center gap-2">
        <Checkbox
          id={ackId}
          checked={acknowledged}
          onCheckedChange={(checked) => {
            if (checked === true) onAcknowledge(conflict.conflictId)
          }}
        />
        <Label htmlFor={ackId} className="text-amber-900 dark:text-amber-100">
          I acknowledge this conflict
        </Label>
      </div>
    </div>
  )
}
