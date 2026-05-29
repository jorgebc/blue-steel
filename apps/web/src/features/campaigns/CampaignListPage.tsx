import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useCampaigns } from '@/api/campaigns'
import { useAuthStore } from '@/store/authStore'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Card, CardHeader, CardTitle } from '@/components/ui/card'
import type { CampaignRole } from '@/types/campaign'

function CampaignCardSkeleton() {
  return (
    <div className="rounded-2xl border border-slate-200 p-6">
      <div className="h-5 w-1/2 animate-pulse rounded bg-slate-200" />
    </div>
  )
}

function RoleBadge({ role }: { role: CampaignRole | null }) {
  if (!role) return null
  return (
    <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium capitalize text-slate-600">
      {role}
    </span>
  )
}

/**
 * Authenticated landing page: lists the caller's campaigns as cards linking to
 * each campaign home. Shows a skeleton on first load, an inline error on
 * failure, and an empty state when the user belongs to no campaigns.
 */
export function CampaignListPage() {
  const { data: campaigns, isLoading, isError } = useCampaigns()
  const isAdmin = useAuthStore((s) => s.currentUser?.isAdmin)
  const [dismissed, setDismissed] = useState(false)

  return (
    <main className="mx-auto max-w-3xl p-6">
      <div className="mb-6 flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Your campaigns</h1>
        {isAdmin && (
          <Link
            to="/campaigns/new"
            className="rounded-lg bg-blue-500 px-4 py-2 text-sm font-medium text-white hover:bg-blue-600"
          >
            New campaign
          </Link>
        )}
      </div>

      {isLoading ? (
        <div className="flex flex-col gap-4">
          <CampaignCardSkeleton />
          <CampaignCardSkeleton />
          <CampaignCardSkeleton />
        </div>
      ) : isError ? (
        !dismissed && (
          <InlineBanner
            variant="error"
            message="We couldn't load your campaigns. Please try again."
            onDismiss={() => setDismissed(true)}
          />
        )
      ) : campaigns && campaigns.length > 0 ? (
        <ul className="flex flex-col gap-4">
          {campaigns.map((campaign) => (
            <li key={campaign.id}>
              <Link
                to={`/campaigns/${campaign.id}`}
                className="block rounded-2xl transition-colors hover:bg-slate-50"
              >
                <Card className="rounded-2xl">
                  <CardHeader className="flex-row items-center justify-between gap-3">
                    <CardTitle>{campaign.name}</CardTitle>
                    <RoleBadge role={campaign.role} />
                  </CardHeader>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-slate-500">
          No campaigns yet — ask your GM or an admin to add you.
        </p>
      )}
    </main>
  )
}
