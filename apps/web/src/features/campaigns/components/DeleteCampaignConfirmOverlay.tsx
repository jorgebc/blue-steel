import { Loader2 } from 'lucide-react'
import { FocusedOverlay } from '@/components/domain/FocusedOverlay'
import { Button } from '@/components/ui/button'

interface Props {
  open: boolean
  campaignName: string
  onConfirm: () => void
  onClose: () => void
  isPending: boolean
}

/**
 * Confirmation for permanently deleting a campaign, as a {@link FocusedOverlay}
 * (modals forbidden, D-082). All campaign data is deleted by DB cascade.
 */
export function DeleteCampaignConfirmOverlay({
  open,
  campaignName,
  onConfirm,
  onClose,
  isPending,
}: Props) {
  return (
    <FocusedOverlay open={open} onClose={onClose} ariaLabel="Delete campaign">
      <div className="w-[28rem] max-w-[90vw] bg-white p-6">
        <h3 className="mb-2 text-base font-medium text-slate-900">Delete this campaign?</h3>
        <p className="mb-6 text-sm text-slate-500">
          <span className="font-medium">{campaignName}</span> and all its data — sessions, actors,
          spaces, events, relations, and annotations — will be permanently deleted. This cannot be
          undone.
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
            Delete campaign
          </Button>
        </div>
      </div>
    </FocusedOverlay>
  )
}
