import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTimeline, type TimelineFilters } from '@/api/timeline'
import type { TimelineEvent } from '@/types/timeline'
import { InlineBanner } from '@/components/domain/InlineBanner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { EventCard } from './EventCard'
import { TimelineSkeleton } from '../components/TimelineSkeleton'

const EMPTY_FILTERS: TimelineFilters = {}

/** Trims the raw input strings into a filter object, dropping blank fields. */
function toFilters(inputs: { actor: string; space: string; eventType: string }): TimelineFilters {
  const filters: TimelineFilters = {}
  if (inputs.actor.trim()) filters.actor = inputs.actor.trim()
  if (inputs.space.trim()) filters.space = inputs.space.trim()
  if (inputs.eventType.trim()) filters.eventType = inputs.eventType.trim()
  return filters
}

interface SessionGroup {
  sessionSequenceNumber: number
  events: TimelineEvent[]
}

/**
 * Collapses the already session-ordered feed into consecutive per-session groups so the ordering
 * axis (session sequence) is visible. Events carry no in-world date, so grouping by session is the
 * only meaningful chronology available.
 */
function groupBySession(events: TimelineEvent[]): SessionGroup[] {
  const groups: SessionGroup[] = []
  for (const event of events) {
    const last = groups[groups.length - 1]
    if (last && last.sessionSequenceNumber === event.sessionSequenceNumber) {
      last.events.push(event)
    } else {
      groups.push({ sessionSequenceNumber: event.sessionSequenceNumber, events: [event] })
    }
  }
  return groups
}

/**
 * Exploration → Timeline: the keyset-paginated event feed (D-055). Filters apply on submit (not per
 * keystroke); changing the applied filters swaps the query key so the feed resets to the first page.
 */
export function TimelinePage() {
  const navigate = useNavigate()
  const [inputs, setInputs] = useState({ actor: '', space: '', eventType: '' })
  const [appliedFilters, setAppliedFilters] = useState<TimelineFilters>(EMPTY_FILTERS)

  const { data, isLoading, isError, hasNextPage, isFetchingNextPage, fetchNextPage } =
    useTimeline(appliedFilters)

  const events = data?.pages.flatMap((page) => page.events) ?? []
  const hasActiveFilters = Object.keys(appliedFilters).length > 0

  function onApply(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setAppliedFilters(toFilters(inputs))
  }

  function onClear() {
    setInputs({ actor: '', space: '', eventType: '' })
    setAppliedFilters(EMPTY_FILTERS)
  }

  return (
    <section>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-foreground">Timeline</h1>
        <p className="text-sm text-muted-foreground">
          Events recorded across this campaign&apos;s committed sessions.
        </p>
      </div>

      <form onSubmit={onApply} className="mb-6 space-y-3">
        <p className="text-xs font-medium text-muted-foreground">Filter events</p>
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label htmlFor="timeline-actor">Actor</Label>
            <Input
              id="timeline-actor"
              placeholder="e.g. Gandalf"
              value={inputs.actor}
              onChange={(e) => setInputs((s) => ({ ...s, actor: e.target.value }))}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="timeline-space">Space</Label>
            <Input
              id="timeline-space"
              placeholder="e.g. Rivendell"
              value={inputs.space}
              onChange={(e) => setInputs((s) => ({ ...s, space: e.target.value }))}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="timeline-event-type">Event type</Label>
            <Input
              id="timeline-event-type"
              placeholder="e.g. battle"
              value={inputs.eventType}
              onChange={(e) => setInputs((s) => ({ ...s, eventType: e.target.value }))}
            />
          </div>
          <Button type="submit" variant="outline">
            Apply
          </Button>
          {hasActiveFilters && (
            <Button type="button" variant="ghost" onClick={onClear}>
              Clear
            </Button>
          )}
        </div>
      </form>

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message="Could not load the timeline. Please refresh the page."
            onDismiss={() => navigate(0)}
          />
        </div>
      )}

      {isLoading && <TimelineSkeleton />}

      {!isLoading && !isError && events.length === 0 && (
        <p className="text-sm text-muted-foreground">
          {hasActiveFilters ? 'No events match these filters.' : 'No events yet.'}
        </p>
      )}

      {!isLoading && !isError && events.length > 0 && (
        <>
          <div className="space-y-6">
            {groupBySession(events).map((group) => (
              <div key={group.sessionSequenceNumber}>
                <h2 className="mb-2 text-sm font-semibold text-foreground">
                  Session #{group.sessionSequenceNumber}
                </h2>
                <ul className="space-y-3">
                  {group.events.map((event) => (
                    <li key={event.eventId}>
                      <EventCard event={event} />
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>

          <div className="mt-6 flex justify-center">
            <Button
              type="button"
              variant="outline"
              disabled={!hasNextPage || isFetchingNextPage}
              onClick={() => fetchNextPage()}
            >
              {isFetchingNextPage ? 'Loading…' : 'Load more'}
            </Button>
          </div>
        </>
      )}
    </section>
  )
}
