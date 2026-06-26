import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Download } from 'lucide-react'
import { useExportCampaign } from '@/api/campaigns'
import { downloadBlob } from '@/lib/downloadBlob'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Button } from '@/components/ui/button'

type Banner = { variant: 'success' | 'error'; message: string }

interface Props {
  campaignId: string
}

/**
 * Danger-zone action that downloads the full campaign archive. Non-destructive,
 * so it needs no confirmation overlay — it saves the returned blob directly and
 * reports the outcome inline.
 */
export function CampaignExportButton({ campaignId }: Props) {
  const { t } = useTranslation()
  const [banner, setBanner] = useState<Banner | null>(null)
  const { mutate, isPending } = useExportCampaign()

  function handleExport() {
    setBanner(null)
    mutate(campaignId, {
      onSuccess({ blob, filename }) {
        downloadBlob(blob, filename)
        setBanner({ variant: 'success', message: t('campaigns.exportSuccess') })
      },
      onError() {
        setBanner({ variant: 'error', message: t('campaigns.exportError') })
      },
    })
  }

  return (
    <div>
      <p className="mb-4 text-sm text-muted-foreground">{t('campaigns.exportDescription')}</p>
      {banner && (
        <div className="mb-4">
          <InlineBanner
            variant={banner.variant}
            message={banner.message}
            onDismiss={() => setBanner(null)}
          />
        </div>
      )}
      <Button type="button" variant="outline" onClick={handleExport} disabled={isPending}>
        <Download className="h-4 w-4" aria-hidden />
        {isPending ? t('campaigns.exporting') : t('campaigns.exportCampaign')}
      </Button>
    </div>
  )
}
