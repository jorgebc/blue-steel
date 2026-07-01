import { Link, useParams } from 'react-router-dom'
import { Badge } from '@/components/ui/badge'
import type { TimelineEvent } from '@/types/timeline'

interface Props {
  event: TimelineEvent
}

/**
 * One Timeline feed row: the event name with its type, involved actors, and space, linking through
 * to the event detail page (D-009). The originating session is shown by the feed's session grouping.
 */
export function EventCard({ event }: Props) {
  const { campaignId } = useParams<{ campaignId: string }>()

  const hasSubline = event.involvedActorNames.length > 0 || Boolean(event.spaceName)

  return (
    <Link
      to={`/campaigns/${campaignId}/explore/events/${event.eventId}`}
      className="flex items-center gap-4 rounded-2xl border border-border bg-surface p-4 shadow-sm transition-shadow duration-200 hover:shadow-md"
    >
      <div className="min-w-0 flex-1 space-y-1">
        <span className="block truncate font-medium text-foreground">{event.name}</span>
        {hasSubline && (
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
          </p>
        )}
      </div>
      {event.eventType && (
        <Badge variant="outline" className="shrink-0 bg-muted capitalize text-muted-foreground">
          {event.eventType}
        </Badge>
      )}
    </Link>
  )
}
