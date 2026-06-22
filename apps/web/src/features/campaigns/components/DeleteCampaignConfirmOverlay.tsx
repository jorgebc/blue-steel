import { Loader2 } from 'lucide-react'
import { Trans, useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
  return (
    <FocusedOverlay open={open} onClose={onClose} ariaLabel={t('campaigns.deleteCampaign')}>
      <div className="w-[28rem] max-w-[90vw] bg-surface p-6">
        <h3 className="mb-2 text-base font-medium text-foreground">
          {t('campaigns.deleteConfirmTitle')}
        </h3>
        <p className="mb-6 text-sm text-muted-foreground">
          <Trans
            i18nKey="campaigns.deleteConfirmBody"
            values={{ campaignName }}
            components={{ strong: <span className="font-medium" /> }}
          />
        </p>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose} disabled={isPending}>
            {t('common.cancel')}
          </Button>
          <Button
            type="button"
            onClick={onConfirm}
            disabled={isPending}
            aria-disabled={isPending}
            className="bg-red-600 text-white hover:bg-red-700"
          >
            {isPending && <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden />}
            {t('campaigns.deleteCampaign')}
          </Button>
        </div>
      </div>
    </FocusedOverlay>
  )
}
