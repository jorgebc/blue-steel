import { Loader2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
  return (
    <FocusedOverlay open={open} onClose={onClose} ariaLabel={t('input.discardDraft')}>
      <div className="w-[24rem] max-w-[90vw] bg-surface p-6">
        <h3 className="mb-2 text-base font-medium text-foreground">{t('input.discardTitle')}</h3>
        <p className="mb-6 text-sm text-muted-foreground">{t('input.discardBody')}</p>
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
            {t('input.discardDraft')}
          </Button>
        </div>
      </div>
    </FocusedOverlay>
  )
}
