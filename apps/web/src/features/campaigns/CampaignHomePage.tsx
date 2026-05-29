import { useParams } from 'react-router-dom'
import { useCampaign } from '@/api/campaigns'

/**
 * Per-campaign landing page reached on entering a campaign (and after a
 * successful commit). Navigation now lives in the app-shell sidebar, so this
 * page is the campaign's welcome surface.
 */
export function CampaignHomePage() {
  const { campaignId } = useParams<{ campaignId: string }>()
  const { data: campaign } = useCampaign(campaignId)

  return (
    <main className="mx-auto max-w-3xl p-6">
      <h1 className="mb-2 text-2xl font-semibold">{campaign?.name ?? 'Campaign'}</h1>
      <p className="text-sm text-slate-500">
        Welcome back. Use the sidebar to add a session or explore your world.
      </p>
    </main>
  )
}
