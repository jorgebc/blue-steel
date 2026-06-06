import { Link, useNavigate } from 'react-router-dom'
import { ChevronLeft } from 'lucide-react'
import { useEntityDetail } from '@/api/worldstate'
import { AnnotationThread } from '@/components/domain/AnnotationThread'
import { EntityVersionHistory } from '@/components/domain/EntityVersionHistory'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { ProposeChangeButton } from '@/components/domain/ProposeChangeButton'
import { EntityLinks } from './EntityLinks'
import { EntityProfileSkeleton } from './EntityProfileSkeleton'
import type { EntityType, EntityVersion } from '@/types/worldstate'

interface Props {
  entityType: EntityType
  entityId: string
  /** Absolute path back to the originating list view. */
  backTo: string
  backLabel: string
}

function latestVersion(versions: EntityVersion[]): EntityVersion | null {
  return versions.reduce<EntityVersion | null>(
    (latest, v) => (latest === null || v.versionNumber > latest.versionNumber ? v : latest),
    null
  )
}

/**
 * Read-only entity profile: current state (latest `fullSnapshot`) + version history (D-001). Hosts
 * comment-marked slots for the annotation thread (F4.4) and a disabled propose-change stub (D-012).
 * Shared by the actor and space profile pages.
 */
export function EntityProfileView({ entityType, entityId, backTo, backLabel }: Props) {
  const navigate = useNavigate()
  const { data, isLoading, isError } = useEntityDetail(entityType, entityId)
  const latest = data ? latestVersion(data.versions) : null

  return (
    <section>
      <Link
        to={backTo}
        className="mb-6 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-slate-900 transition-colors duration-200"
      >
        <ChevronLeft className="h-4 w-4" aria-hidden />
        {backLabel}
      </Link>

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message="Could not load this profile. Please refresh the page."
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
            <ProposeChangeButton />
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

          <EntityLinks entityType={entityType} entityId={entityId} />

          <AnnotationThread entityType={entityType} entityId={entityId} />
        </div>
      )}
    </section>
  )
}
