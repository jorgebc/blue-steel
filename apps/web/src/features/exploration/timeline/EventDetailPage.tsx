import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ChevronLeft } from 'lucide-react'
import { useEntityDetail } from '@/api/worldstate'
import { AnnotationThread } from '@/components/domain/AnnotationThread'
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
  const { t } = useTranslation()
  const { campaignId, eventId } = useParams<{ campaignId: string; eventId: string }>()
  const navigate = useNavigate()
  const { data, isLoading, isError } = useEntityDetail('event', eventId ?? '')
  const latest = data ? latestVersion(data.versions) : null

  return (
    <section>
      <Link
        to={`/campaigns/${campaignId}/explore/timeline`}
        className="mb-6 inline-flex items-center gap-1 text-sm text-muted-foreground transition-colors duration-200 hover:text-foreground"
      >
        <ChevronLeft className="h-4 w-4" aria-hidden />
        {t('exploration.backToTimeline')}
      </Link>

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message={t('exploration.eventLoadError')}
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
              <p className="text-sm capitalize text-muted-foreground">{data.entityType}</p>
            </div>
            {/* Propose-change affordance — disabled stub in v1 (D-012). */}
            <Button
              type="button"
              variant="outline"
              disabled
              title={t('exploration.eventDetail.proposeComingSoon')}
            >
              {t('proposals.proposeChange')}
            </Button>
          </div>

          <div className="rounded-2xl border border-border bg-surface p-6 shadow-sm">
            <h2 className="mb-3 text-sm font-semibold text-foreground">
              {t('exploration.currentState')}
            </h2>
            {latest && Object.keys(latest.fullSnapshot).length > 0 ? (
              <dl className="space-y-2 text-sm">
                {Object.entries(latest.fullSnapshot).map(([key, value]) => (
                  <div key={key} className="flex gap-4">
                    <dt className="w-40 shrink-0 font-medium text-muted-foreground">{key}</dt>
                    <dd className="text-foreground">{String(value)}</dd>
                  </div>
                ))}
              </dl>
            ) : (
              <p className="text-sm text-muted-foreground">{t('exploration.noRecordedState')}</p>
            )}
          </div>

          <div>
            <h2 className="mb-3 text-sm font-semibold text-foreground">
              {t('exploration.versionHistory')}
            </h2>
            <EntityVersionHistory versions={data.versions} />
          </div>

          <AnnotationThread entityType="event" entityId={eventId ?? ''} />
        </div>
      )}
    </section>
  )
}
