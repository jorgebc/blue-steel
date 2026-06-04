import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTimeline, type TimelineFilters } from '@/api/timeline'
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

/**
 * Exploration → Timeline: the keyset-paginated event feed (D-055). Filters apply on submit (not per
 * keystroke); changing the applied filters swaps the query key so the feed resets to the first page.
 */
export function TimelinePage() {
  const navigate = useNavigate()
  const [inputs, setInputs] = useState({ actor: '', space: '', eventType: '' })
  const [appliedFilters, setAppliedFilters] = useState<TimelineFilters>(EMPTY_FILTERS)

  const {
    data,
    isLoading,
    isError,
    hasNextPage,
    isFetchingNextPage,
    fetchNextPage,
  } = useTimeline(appliedFilters)

  const events = data?.pages.flatMap((page) => page.events) ?? []

  function onApply(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setAppliedFilters(toFilters(inputs))
  }

  return (
    <section>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-slate-900">Timeline</h1>
        <p className="text-sm text-slate-500">
          Events recorded across this campaign&apos;s committed sessions.
        </p>
      </div>

      <form onSubmit={onApply} className="mb-6 flex flex-wrap items-end gap-3">
        <div className="space-y-1">
          <Label htmlFor="timeline-actor">Actor</Label>
          <Input
            id="timeline-actor"
            value={inputs.actor}
            onChange={(e) => setInputs((s) => ({ ...s, actor: e.target.value }))}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="timeline-space">Space</Label>
          <Input
            id="timeline-space"
            value={inputs.space}
            onChange={(e) => setInputs((s) => ({ ...s, space: e.target.value }))}
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor="timeline-event-type">Event type</Label>
          <Input
            id="timeline-event-type"
            value={inputs.eventType}
            onChange={(e) => setInputs((s) => ({ ...s, eventType: e.target.value }))}
          />
        </div>
        <Button type="submit" variant="outline">
          Apply
        </Button>
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
        <p className="text-sm text-slate-500">No events yet.</p>
      )}

      {!isLoading && !isError && events.length > 0 && (
        <>
          <ul className="space-y-3">
            {events.map((event) => (
              <li key={event.eventId}>
                <EventCard event={event} />
              </li>
            ))}
          </ul>

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
