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
 * is linked to, and the sessions it appears in. Related entities link to their profile and events to
 * their detail page; relations and session appearances render as non-navigable rows until their
 * detail pages exist (F4.8).
 */
export function EntityLinks({ entityType, entityId }: Props) {
  const { campaignId } = useParams<{ campaignId: string }>()
  const navigate = useNavigate()
  const { data, isLoading, isError } = useEntityLinks(entityType, entityId)

  if (isError) {
    return (
      <section aria-label="Connections">
        <InlineBanner
          variant="error"
          message="Could not load connections. Please refresh the page."
          onDismiss={() => navigate(0)}
        />
      </section>
    )
  }

  if (isLoading || !data) {
    return (
      <div role="status" aria-label="Loading connections" className="space-y-6">
        {[0, 1].map((i) => (
          <div key={i}>
            <div className="mb-3 h-4 w-32 animate-pulse rounded bg-slate-200" />
            <div className="space-y-2">
              <div className="h-14 animate-pulse rounded-2xl bg-slate-200" />
              <div className="h-14 animate-pulse rounded-2xl bg-slate-200" />
            </div>
          </div>
        ))}
      </div>
    )
  }

  const sessionCount = data.appearanceSessionIds.length

  return (
    <section aria-label="Connections" className="space-y-6">
      <div>
        <h2 className="mb-3 text-sm font-semibold text-slate-700">Relations</h2>
        {data.relations.length === 0 ? (
          <p className="text-sm text-slate-500">No relations.</p>
        ) : (
          <ul className="space-y-2">
            {data.relations.map((relation) => (
              <li
                key={relation.relationId}
                className="flex items-center gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
              >
                <span className="flex-1 truncate font-medium text-slate-900">{relation.name}</span>
                {relation.kind && (
                  <Badge
                    variant="outline"
                    className="shrink-0 bg-slate-100 capitalize text-slate-600"
                  >
                    {relation.kind}
                  </Badge>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <h2 className="mb-3 text-sm font-semibold text-slate-700">Related entities</h2>
        {data.relatedEntities.length === 0 ? (
          <p className="text-sm text-slate-500">No related entities.</p>
        ) : (
          <ul className="space-y-2">
            {data.relatedEntities.map((entity) => (
              <li key={entity.entityId}>
                <Link
                  to={`/campaigns/${campaignId}/explore/${PROFILE_SEGMENT[entity.entityType] ?? 'entities'}/${entity.entityId}`}
                  className="flex items-center gap-4 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm transition-shadow duration-200 hover:shadow-md"
                >
                  <span className="flex-1 truncate font-medium text-slate-900">{entity.name}</span>
                  <Badge
                    variant="outline"
                    className="shrink-0 bg-slate-100 capitalize text-slate-600"
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
        <h2 className="mb-3 text-sm font-semibold text-slate-700">Events</h2>
        {data.events.length === 0 ? (
          <p className="text-sm text-slate-500">No events.</p>
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
        <h2 className="mb-3 text-sm font-semibold text-slate-700">
          {sessionCount > 0
            ? `Appears in ${sessionCount} session${sessionCount === 1 ? '' : 's'}`
            : 'Appears in sessions'}
        </h2>
        {sessionCount === 0 ? (
          <p className="text-sm text-slate-500">Does not appear in any sessions.</p>
        ) : (
          <ul className="flex flex-wrap gap-2">
            {data.appearanceSessionIds.map((sessionId) => (
              <li key={sessionId}>
                <Badge variant="outline" className="bg-slate-100 font-mono text-slate-600">
                  {sessionId.slice(0, 8)}
                </Badge>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}
