import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { ChevronLeft } from 'lucide-react'
import { useRelationDetail } from '@/api/relations'
import { EntityVersionHistory } from '@/components/domain/EntityVersionHistory'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Badge } from '@/components/ui/badge'
import { EntityProfileSkeleton } from '../components/EntityProfileSkeleton'

/** Maps a relation endpoint's entity type to its exploration profile route segment. */
const PROFILE_SEGMENT: Record<string, string> = {
  actor: 'entities',
  space: 'spaces',
}

interface EndpointProps {
  role: 'Source' | 'Target'
  campaignId: string | undefined
  entityId: string | null
  entityType: string | null
}

/**
 * One relation endpoint. Resolved endpoints link to the entity profile; an endpoint that could not
 * be name-matched at commit (D-095) renders as a non-navigable "Unresolved" row.
 */
function Endpoint({ role, campaignId, entityId, entityType }: EndpointProps) {
  const { t } = useTranslation()
  const segment = entityType ? PROFILE_SEGMENT[entityType] : undefined
  const roleLabel = role === 'Source' ? t('exploration.source') : t('exploration.target')

  return (
    <div className="rounded-2xl border border-border bg-surface p-4 shadow-sm">
      <p className="mb-1 text-xs font-medium uppercase tracking-wide text-muted-foreground">
        {roleLabel}
      </p>
      {entityId && segment ? (
        <Link
          to={`/campaigns/${campaignId}/explore/${segment}/${entityId}`}
          className="inline-flex items-center gap-2 font-medium text-accent hover:underline"
        >
          <span className="capitalize">{entityType}</span>
        </Link>
      ) : (
        <span className="text-sm text-muted-foreground">{t('exploration.unresolved')}</span>
      )}
    </div>
  )
}

/**
 * Exploration → read-only relation detail (F4.8.2), reached by click-through from an entity
 * profile's Relations section or the relations graph. Shows the relation name, kind, its two
 * endpoints (linked to their entity profiles) and full version history (D-001). Strictly read-only
 * (D-010).
 */
export function RelationDetailPage() {
  const { campaignId, relationId } = useParams<{ campaignId: string; relationId: string }>()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { data, isLoading, isError } = useRelationDetail(relationId ?? '')

  return (
    <section>
      <Link
        to={`/campaigns/${campaignId}/explore/relations`}
        className="mb-6 inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors duration-200 hover:text-foreground"
      >
        <ChevronLeft className="h-4 w-4" aria-hidden />
        {t('exploration.backToRelations')}
      </Link>

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message={t('exploration.relationLoadError')}
            onDismiss={() => navigate(0)}
          />
        </div>
      )}

      {isLoading && <EntityProfileSkeleton />}

      {!isLoading && !isError && data && (
        <div className="space-y-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h1 className="text-2xl font-semibold text-foreground">{data.name}</h1>
              <p className="text-sm text-muted-foreground">{t('exploration.relation')}</p>
            </div>
            {data.kind && (
              <Badge
                variant="outline"
                className="shrink-0 bg-muted capitalize text-muted-foreground"
              >
                {data.kind}
              </Badge>
            )}
          </div>

          <div>
            <h2 className="mb-3 text-sm font-semibold text-foreground">
              {t('exploration.endpoints')}
            </h2>
            <div className="grid gap-2 sm:grid-cols-2">
              <Endpoint
                role="Source"
                campaignId={campaignId}
                entityId={data.sourceEntityId}
                entityType={data.sourceEntityType}
              />
              <Endpoint
                role="Target"
                campaignId={campaignId}
                entityId={data.targetEntityId}
                entityType={data.targetEntityType}
              />
            </div>
          </div>

          <div>
            <h2 className="mb-3 text-sm font-semibold text-foreground">
              {t('exploration.versionHistory')}
            </h2>
            <EntityVersionHistory versions={data.versions} />
          </div>
        </div>
      )}
    </section>
  )
}
