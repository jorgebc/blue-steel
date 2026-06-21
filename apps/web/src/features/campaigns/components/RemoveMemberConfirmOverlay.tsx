import { Loader2 } from 'lucide-react'
import { FocusedOverlay } from '@/components/domain/FocusedOverlay'
import { Button } from '@/components/ui/button'

interface Props {
  open: boolean
  memberEmail: string | null
  onConfirm: () => void
  onClose: () => void
  isPending: boolean
}

/**
 * Confirmation for removing a campaign member, as a {@link FocusedOverlay}
 * (modals forbidden, D-082). ESC/backdrop cancel via the overlay.
 */
export function RemoveMemberConfirmOverlay({
  open,
  memberEmail,
  onConfirm,
  onClose,
  isPending,
}: Props) {
  return (
    <FocusedOverlay open={open} onClose={onClose} ariaLabel="Remove member">
      <div className="w-[24rem] max-w-[90vw] bg-surface p-6">
        <h3 className="mb-2 text-base font-medium text-foreground">Remove this member?</h3>
        <p className="mb-6 text-sm text-muted-foreground">
          {memberEmail ? <span className="font-medium">{memberEmail}</span> : 'This member'} will
          lose access to the campaign. They can be invited again later.
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
            Remove member
          </Button>
        </div>
      </div>
    </FocusedOverlay>
  )
}
