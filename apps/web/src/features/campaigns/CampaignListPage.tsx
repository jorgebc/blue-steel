import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Library } from 'lucide-react'
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
        {isAdmin && campaigns && campaigns.length > 0 && (
          <div className="flex items-center gap-2">
            <Link
              to="/invite"
              className="rounded-lg border border-slate-200 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Invite user
            </Link>
            <Link
              to="/campaigns/new"
              className="rounded-lg bg-blue-500 px-4 py-2 text-sm font-medium text-white hover:bg-blue-600"
            >
              New campaign
            </Link>
          </div>
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
        <div className="flex flex-col items-center rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-sm">
          <Library className="h-10 w-10 text-slate-400" aria-hidden />
          <h2 className="mt-4 text-base font-medium text-slate-900">No campaigns yet</h2>
          {isAdmin ? (
            <>
              <p className="mt-1 text-sm text-slate-500">
                Create your first campaign to start building its world.
              </p>
              <Link
                to="/campaigns/new"
                className="mt-6 rounded-lg bg-blue-500 px-4 py-2 text-sm font-medium text-white hover:bg-blue-600"
              >
                New campaign
              </Link>
            </>
          ) : (
            <p className="mt-1 text-sm text-slate-500">
              Ask your GM or an admin to add you to a campaign.
            </p>
          )}
        </div>
      )}
    </main>
  )
}
