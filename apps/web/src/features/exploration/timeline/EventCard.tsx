import { Link, useParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import type { TimelineEvent } from '@/types/timeline'

interface Props {
  event: TimelineEvent
}

/**
 * One Timeline feed row: the event name with its type, involved actors, space, and originating
 * session, linking through to the event detail page (D-009).
 */
export function EventCard({ event }: Props) {
  const { campaignId } = useParams<{ campaignId: string }>()

  return (
    <Link
      to={`/campaigns/${campaignId}/explore/events/${event.eventId}`}
      className="flex items-center gap-4 rounded-2xl border border-border bg-surface p-4 shadow-sm transition-shadow duration-200 hover:shadow-md"
    >
      <div className="min-w-0 flex-1 space-y-1">
        <span className="block truncate font-medium text-foreground">{event.name}</span>
        <p className="truncate text-sm text-muted-foreground">
          {event.involvedActorNames.length > 0 && (
            <span>{event.involvedActorNames.join(', ')}</span>
          )}
          {event.spaceName && (
            <span>
              {event.involvedActorNames.length > 0 ? ' · ' : ''}
              {event.spaceName}
            </span>
          )}
          {(event.involvedActorNames.length > 0 || event.spaceName) && <span> · </span>}
          <span>Session #{event.sessionSequenceNumber}</span>
        </p>
      </div>
      {event.eventType && (
        <Badge variant="outline" className="shrink-0 bg-muted capitalize text-muted-foreground">
          {event.eventType}
        </Badge>
      )}
    </Link>
  )
}
