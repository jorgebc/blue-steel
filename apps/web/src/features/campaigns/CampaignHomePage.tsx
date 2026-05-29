import { Link, useParams } from 'react-router-dom'
import { useCampaign } from '@/api/campaigns'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * Per-campaign home reached on entering a campaign (and after a successful
 * commit). Shows the campaign identity, a live entry into Input Mode, and
 * disabled "coming soon" stubs for the modes that ship in later phases.
 */
export function CampaignHomePage() {
  const { campaignId } = useParams<{ campaignId: string }>()
  const { data: campaign } = useCampaign(campaignId)

  return (
    <main className="mx-auto max-w-3xl p-6">
      <h1 className="mb-2 text-2xl font-semibold">{campaign?.name ?? 'Campaign'}</h1>
      <p className="mb-8 text-sm text-slate-500">Choose where to go next.</p>

      <div className="grid gap-4 sm:grid-cols-3">
        <Link
          to={`/campaigns/${campaignId}/sessions/new`}
          className="block rounded-2xl transition-colors hover:bg-slate-50"
        >
          <Card className="rounded-2xl">
            <CardHeader>
              <CardTitle>New session</CardTitle>
              <CardDescription>Submit a session summary for processing.</CardDescription>
            </CardHeader>
          </Card>
        </Link>

        <Card className="rounded-2xl opacity-60" aria-disabled="true">
          <CardHeader>
            <CardTitle>Query</CardTitle>
            <CardDescription>Ask questions about your world state.</CardDescription>
            <span className="text-xs text-slate-500">Coming soon</span>
          </CardHeader>
        </Card>

        <Card className="rounded-2xl opacity-60" aria-disabled="true">
          <CardHeader>
            <CardTitle>Exploration</CardTitle>
            <CardDescription>Browse actors, events, and locations.</CardDescription>
            <span className="text-xs text-slate-500">Coming soon</span>
          </CardHeader>
        </Card>
      </div>
    </main>
  )
}
