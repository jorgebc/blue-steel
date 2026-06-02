import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { History, Sparkles } from 'lucide-react'
import { useCampaign, useDeleteCampaign } from '@/api/campaigns'
import { useAuthStore } from '@/store/authStore'
import { useCampaignStore } from '@/store/campaignStore'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Button } from '@/components/ui/button'
import { MemberManagementPanel } from './components/MemberManagementPanel'
import { DeleteCampaignConfirmOverlay } from './components/DeleteCampaignConfirmOverlay'

/**
 * Per-campaign landing page reached on entering a campaign (and after a
 * successful commit). Navigation now lives in the app-shell sidebar, so this
 * page is the campaign's welcome surface.
 */
export function CampaignHomePage() {
  const { campaignId } = useParams<{ campaignId: string }>()
  const { data: campaign } = useCampaign(campaignId)
  const navigate = useNavigate()
  const isAdmin = useAuthStore((s) => s.currentUser?.isAdmin)
  const clearCampaign = useCampaignStore((s) => s.clearCampaign)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [deleteError, setDeleteError] = useState(false)
  const { mutate: doDelete, isPending } = useDeleteCampaign()

  function handleDeleteConfirm() {
    if (!campaignId) return
    doDelete(campaignId, {
      onSuccess() {
        clearCampaign()
        navigate('/')
      },
      onError() {
        setDeleteOpen(false)
        setDeleteError(true)
      },
    })
  }

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

      {campaignId && (
        <div className="mt-8">
          <Link
            to={`/campaigns/${campaignId}/sessions`}
            className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-shadow duration-200 hover:shadow-md"
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-500">
              <History className="h-5 w-5" aria-hidden />
            </div>
            <div>
              <p className="text-sm font-medium text-slate-900">Session history</p>
              <p className="text-xs text-slate-500">Browse past and in-progress sessions.</p>
            </div>
          </Link>
        </div>
      )}

      {campaignId && campaign?.role === 'gm' && <MemberManagementPanel campaignId={campaignId} />}

      {isAdmin && campaignId && (
        <div className="mt-10 border-t border-red-100 pt-6">
          <h2 className="mb-1 text-sm font-medium text-red-700">Danger zone</h2>
          <p className="mb-4 text-sm text-slate-500">
            Permanently delete this campaign and all its data. This cannot be undone.
          </p>
          {deleteError && (
            <div className="mb-4">
              <InlineBanner
                variant="error"
                message="Failed to delete the campaign. Please try again."
                onDismiss={() => setDeleteError(false)}
              />
            </div>
          )}
          <Button
            type="button"
            variant="outline"
            className="border-red-300 text-red-700 hover:bg-red-50"
            onClick={() => setDeleteOpen(true)}
          >
            Delete campaign
          </Button>
          <DeleteCampaignConfirmOverlay
            open={deleteOpen}
            campaignName={campaign?.name ?? ''}
            onClose={() => setDeleteOpen(false)}
            onConfirm={handleDeleteConfirm}
            isPending={isPending}
          />
        </div>
      )}
    </main>
  )
}
