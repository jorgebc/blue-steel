import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, Outlet, useParams } from 'react-router-dom'
import { useCampaign } from '@/api/campaigns'
import { useCampaignStore } from '@/store/campaignStore'
import { InlineBanner } from '@/components/domain/InlineBanner'

/** DTO-derived placeholder mirroring the campaign page header while it loads. */
function CampaignContextSkeleton() {
  return (
    <main className="mx-auto max-w-3xl p-6" aria-hidden="true">
      <div className="mb-2 h-8 w-1/2 animate-pulse rounded bg-muted" />
      <div className="mb-8 h-4 w-1/3 animate-pulse rounded bg-muted" />
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="h-28 animate-pulse rounded-2xl bg-muted" />
        <div className="h-28 animate-pulse rounded-2xl bg-muted" />
        <div className="h-28 animate-pulse rounded-2xl bg-muted" />
      </div>
    </main>
  )
}

/**
 * Route guard for `/campaigns/:campaignId`. Loads the campaign, writes the
 * active campaign id + the caller's role (resolved server-side, never from the
 * JWT) into the campaign store, and renders the campaign subtree only once that
 * context is set — so nested campaign-scoped routes can read `activeRole`.
 * Shows a skeleton while resolving and an inline error with a link home when the
 * campaign is missing or forbidden (403/404).
 */
export function CampaignContextGuard() {
  const { t } = useTranslation()
  const { campaignId } = useParams<{ campaignId: string }>()
  const { data, isLoading, isError } = useCampaign(campaignId)
  const setCampaign = useCampaignStore((state) => state.setCampaign)
  const activeCampaignId = useCampaignStore((state) => state.activeCampaignId)
  const [dismissed, setDismissed] = useState(false)

  useEffect(() => {
    if (data && campaignId) {
      setCampaign(campaignId, data.role)
    }
  }, [data, campaignId, setCampaign])

  if (isError) {
    return (
      <main className="mx-auto flex min-h-screen max-w-md flex-col items-center justify-center gap-4 p-6">
        {!dismissed && (
          <InlineBanner
            variant="error"
            message={t('common.campaignLoadError')}
            onDismiss={() => setDismissed(true)}
          />
        )}
        <Link to="/" className="text-sm text-accent underline-offset-4 hover:underline">
          {t('common.backToCampaigns')}
        </Link>
      </main>
    )
  }

  // Hold the skeleton while loading and until the store reflects this campaign,
  // so children never render with stale or absent campaign context.
  if (isLoading || activeCampaignId !== campaignId) {
    return <CampaignContextSkeleton />
  }

  return <Outlet />
}
