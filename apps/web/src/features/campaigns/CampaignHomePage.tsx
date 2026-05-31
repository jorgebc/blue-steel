import { useParams } from 'react-router-dom'
import { Sparkles } from 'lucide-react'
import { useCampaign } from '@/api/campaigns'
import { MemberManagementPanel } from './components/MemberManagementPanel'

/**
 * Per-campaign landing page reached on entering a campaign (and after a
 * successful commit). Navigation now lives in the app-shell sidebar, so this
 * page is the campaign's welcome surface.
 */
export function CampaignHomePage() {
  const { campaignId } = useParams<{ campaignId: string }>()
  const { data: campaign } = useCampaign(campaignId)

  return (
    <main className="mx-auto max-w-3xl p-8">
      <div className="flex items-start gap-4">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-500">
          <Sparkles className="h-6 w-6" aria-hidden />
        </div>
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">{campaign?.name ?? 'Campaign'}</h1>
          <p className="mt-2 max-w-prose text-sm text-slate-500">
            Welcome back. Use the sidebar to add a session or explore your world as it grows.
          </p>
        </div>
      </div>

      {campaignId && campaign?.role === 'gm' && <MemberManagementPanel campaignId={campaignId} />}
    </main>
  )
}
