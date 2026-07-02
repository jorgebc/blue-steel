import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useEntityLinks } from '@/api/worldstate'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Badge } from '@/components/ui/badge'
import { EventCard } from '../timeline/EventCard'
import type { EntityType } from '@/types/worldstate'

/** Maps a related entity's type to its exploration profile route segment. */
const PROFILE_SEGMENT: Partial<Record<EntityType, string>> = {
  actor: 'entities',
  space: 'spaces',
}

interface Props {
  entityType: EntityType
  entityId: string
}

/**
 * Profile cross-links (F4.7): the entity's relations, the entities at the other end, the events it
 * is linked to, and the sessions it appears in. Every item deep-links: relations to the relation
 * detail page, related entities to their profile, events to event detail, and appearances to the
 * read-only session detail page, labelled "Session #N" (F4.8).
 */
export function EntityLinks({ entityType, entityId }: Props) {
  const { t } = useTranslation()
  const { campaignId } = useParams<{ campaignId: string }>()
  const navigate = useNavigate()
  const { data, isLoading, isError } = useEntityLinks(entityType, entityId)

  if (isError) {
    return (
      <section aria-label={t('exploration.entityLinks.connectionsAria')}>
        <InlineBanner
          variant="error"
          message={t('exploration.entityLinks.loadError')}
          onDismiss={() => navigate(0)}
        />
      </section>
    )
  }

  if (isLoading || !data) {
    return (
      <div
        role="status"
        aria-label={t('exploration.entityLinks.loadingAria')}
        className="space-y-6"
      >
        {[0, 1].map((i) => (
          <div key={i}>
            <div className="mb-3 h-4 w-32 animate-pulse rounded bg-muted" />
            <div className="space-y-2">
              <div className="h-14 animate-pulse rounded-2xl bg-muted" />
              <div className="h-14 animate-pulse rounded-2xl bg-muted" />
            </div>
          </div>
        ))}
      </div>
    )
  }

  const sessionCount = data.appearances.length

  return (
    <section aria-label={t('exploration.entityLinks.connectionsAria')} className="space-y-6">
      <div>
        <h2 className="mb-3 text-sm font-semibold text-foreground">
          {t('exploration.entityLinks.relations')}
        </h2>
        {data.relations.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            {t('exploration.entityLinks.noRelations')}
          </p>
        ) : (
          <ul className="space-y-2">
            {data.relations.map((relation) => (
              <li key={relation.relationId}>
                <Link
                  to={`/campaigns/${campaignId}/explore/relations/${relation.relationId}`}
                  className="flex items-center gap-3 rounded-2xl border border-border bg-surface p-4 shadow-sm transition-shadow duration-200 hover:shadow-md"
                >
                  <span className="flex-1 truncate font-medium text-foreground">
                    {relation.name}
                  </span>
                  {relation.kind && (
                    <Badge
                      variant="outline"
                      className="shrink-0 bg-muted capitalize text-muted-foreground"
                    >
                      {relation.kind}
                    </Badge>
                  )}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <h2 className="mb-3 text-sm font-semibold text-foreground">
          {t('exploration.entityLinks.relatedEntities')}
        </h2>
        {data.relatedEntities.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            {t('exploration.entityLinks.noRelatedEntities')}
          </p>
        ) : (
          <ul className="space-y-2">
            {data.relatedEntities.map((entity) => (
              <li key={entity.entityId}>
                <Link
                  to={`/campaigns/${campaignId}/explore/${PROFILE_SEGMENT[entity.entityType] ?? 'entities'}/${entity.entityId}`}
                  className="flex items-center gap-4 rounded-2xl border border-border bg-surface p-4 shadow-sm transition-shadow duration-200 hover:shadow-md"
                >
                  <span className="flex-1 truncate font-medium text-foreground">{entity.name}</span>
                  <Badge
                    variant="outline"
                    className="shrink-0 bg-muted capitalize text-muted-foreground"
                  >
                    {entity.entityType}
                  </Badge>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <h2 className="mb-3 text-sm font-semibold text-foreground">
          {t('exploration.entityLinks.events')}
        </h2>
        {data.events.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t('exploration.entityLinks.noEvents')}</p>
        ) : (
          <ul className="space-y-2">
            {data.events.map((event) => (
              <li key={event.eventId}>
                <EventCard event={event} />
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <h2 className="mb-3 text-sm font-semibold text-foreground">
          {sessionCount > 0
            ? t('exploration.entityLinks.appearsInCount', { count: sessionCount })
            : t('exploration.entityLinks.appearsIn')}
        </h2>
        {sessionCount === 0 ? (
          <p className="text-sm text-muted-foreground">
            {t('exploration.entityLinks.noAppearances')}
          </p>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {data.appearances.map((appearance) => (
              <li key={appearance.sessionId}>
                <Link
                  to={`/campaigns/${campaignId}/sessions/${appearance.sessionId}`}
                  className="inline-flex rounded-full border border-border bg-surface px-3 py-1 text-sm font-medium text-foreground shadow-sm transition-shadow duration-200 hover:shadow-md"
                >
                  {t('exploration.entityLinks.sessionLink', {
                    sequence: appearance.sequenceNumber,
                  })}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}
