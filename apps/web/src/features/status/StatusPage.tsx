import { useState } from 'react'
import { useHealth } from '@/api/health'
import { InlineBanner } from '@/components/domain/InlineBanner'

function StatusSkeleton() {
  return (
    <div
      role="status"
      aria-label="Loading system status"
      className="rounded-2xl bg-white p-8 shadow-sm"
    >
      <div className="mb-6 h-6 w-1/3 rounded bg-slate-200 animate-pulse" />
      <div className="space-y-4">
        <div className="space-y-2">
          <div className="h-3 w-12 rounded bg-slate-200 animate-pulse" />
          <div className="h-4 w-8 rounded bg-slate-200 animate-pulse" />
        </div>
        <div className="space-y-2">
          <div className="h-3 w-16 rounded bg-slate-200 animate-pulse" />
          <div className="h-4 w-8 rounded bg-slate-200 animate-pulse" />
        </div>
      </div>
    </div>
  )
}

export function StatusPage() {
  const { data, isLoading, error } = useHealth()
  const [bannerDismissed, setBannerDismissed] = useState(false)

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 p-8">
      <div className="w-full max-w-sm">
        {error && !bannerDismissed && (
          <div className="mb-4">
            <InlineBanner
              variant="error"
              message="Unable to reach the server. Please try again later."
              onDismiss={() => setBannerDismissed(true)}
            />
          </div>
        )}
        {isLoading && <StatusSkeleton />}
        {data && (
          <div className="rounded-2xl bg-white p-8 shadow-sm">
            <h1 className="mb-6 text-2xl font-semibold text-slate-900">System status</h1>
            <dl className="space-y-4">
              <div>
                <dt className="text-sm text-slate-500">API</dt>
                <dd className="text-sm font-medium text-slate-900">{data.status}</dd>
              </div>
              <div>
                <dt className="text-sm text-slate-500">Database</dt>
                <dd className="text-sm font-medium text-slate-900">{data.db}</dd>
              </div>
            </dl>
          </div>
        )}
      </div>
    </div>
  )
}
