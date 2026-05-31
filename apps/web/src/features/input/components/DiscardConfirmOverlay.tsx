import { Loader2 } from 'lucide-react'
import { FocusedOverlay } from '@/components/domain/FocusedOverlay'
import { Button } from '@/components/ui/button'

interface Props {
  open: boolean
  onConfirm: () => void
  onClose: () => void
  isPending: boolean
}

/**
 * Confirmation for the destructive discard action, as a {@link FocusedOverlay}
 * (modals forbidden, D-082). ESC/backdrop cancel via the overlay.
 */
export function DiscardConfirmOverlay({ open, onConfirm, onClose, isPending }: Props) {
  return (
    <FocusedOverlay open={open} onClose={onClose} ariaLabel="Discard draft">
      <div className="w-[24rem] max-w-[90vw] bg-white p-6">
        <h3 className="mb-2 text-base font-medium text-slate-900">Discard this draft?</h3>
        <p className="mb-6 text-sm text-slate-500">
          This cannot be undone. The session review will be lost and the campaign unblocked for a
          new submission.
        </p>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={isPending}>
            Cancel
          </Button>
          <Button
            type="button"
            onClick={onConfirm}
            disabled={isPending}
            aria-disabled={isPending}
            className="bg-red-600 text-white hover:bg-red-700"
          >
            {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
            Discard draft
          </Button>
        </div>
      </div>
    </FocusedOverlay>
  )
}
