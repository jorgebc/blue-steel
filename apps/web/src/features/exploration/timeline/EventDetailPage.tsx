import { Link, useNavigate, useParams } from 'react-router-dom'
import { ChevronLeft } from 'lucide-react'
import { useEntityDetail } from '@/api/worldstate'
import { EntityVersionHistory } from '@/components/domain/EntityVersionHistory'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Button } from '@/components/ui/button'
import { EntityProfileSkeleton } from '../components/EntityProfileSkeleton'
import type { EntityVersion } from '@/types/worldstate'

function latestVersion(versions: EntityVersion[]): EntityVersion | null {
  return versions.reduce<EntityVersion | null>(
    (latest, v) => (latest === null || v.versionNumber > latest.versionNumber ? v : latest),
    null
  )
}

/**
 * Exploration → event detail, reached by click-through from the Timeline: current state (latest
 * `fullSnapshot`) + full version history (D-001). Reuses the generic event detail endpoint and
 * reserves the annotation slot (F4.4).
 */
export function EventDetailPage() {
  const { campaignId, eventId } = useParams<{ campaignId: string; eventId: string }>()
  const navigate = useNavigate()
  const { data, isLoading, isError } = useEntityDetail('event', eventId ?? '')
  const latest = data ? latestVersion(data.versions) : null

  return (
    <section>
      <Link
        to={`/campaigns/${campaignId}/explore/timeline`}
        className="mb-6 inline-flex items-center gap-1 text-sm text-slate-500 transition-colors duration-200 hover:text-slate-900"
      >
        <ChevronLeft className="h-4 w-4" aria-hidden />
        Back to timeline
      </Link>

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message="Could not load this event. Please refresh the page."
            onDismiss={() => navigate(0)}
          />
        </div>
      )}

      {isLoading && <EntityProfileSkeleton />}

      {!isLoading && !isError && data && (
        <div className="space-y-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h1 className="text-2xl font-semibold text-slate-900">{data.name}</h1>
              <p className="text-sm capitalize text-slate-500">{data.entityType}</p>
            </div>
            {/* Propose-change affordance — disabled stub in v1 (D-012). */}
            <Button type="button" variant="outline" disabled title="Proposing changes is coming soon">
              Propose a change
            </Button>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <h2 className="mb-3 text-sm font-semibold text-slate-700">Current state</h2>
            {latest && Object.keys(latest.fullSnapshot).length > 0 ? (
              <dl className="space-y-2 text-sm">
                {Object.entries(latest.fullSnapshot).map(([key, value]) => (
                  <div key={key} className="flex gap-4">
                    <dt className="w-40 shrink-0 font-medium text-slate-500">{key}</dt>
                    <dd className="text-slate-900">{String(value)}</dd>
                  </div>
                ))}
              </dl>
            ) : (
              <p className="text-sm text-slate-500">No recorded state.</p>
            )}
          </div>

          <div>
            <h2 className="mb-3 text-sm font-semibold text-slate-700">Version history</h2>
            <EntityVersionHistory versions={data.versions} />
          </div>

          {/* AnnotationThread slot (F4.4) — rendered here once annotations ship. */}
        </div>
      )}
    </section>
  )
}
