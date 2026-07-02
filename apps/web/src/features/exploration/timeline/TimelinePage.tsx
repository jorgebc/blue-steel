import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
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
  const { t } = useTranslation()
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
        <h1 className="text-2xl font-semibold text-foreground">
          {t('exploration.timeline.title')}
        </h1>
        <p className="text-sm text-muted-foreground">{t('exploration.timeline.description')}</p>
      </div>

      <form onSubmit={onApply} className="mb-6 space-y-3">
        <p className="text-xs font-medium text-muted-foreground">
          {t('exploration.timeline.filterEvents')}
        </p>
        <div className="flex flex-wrap items-end gap-3">
          <div className="space-y-1">
            <Label htmlFor="timeline-actor">{t('exploration.timeline.actorLabel')}</Label>
            <Input
              id="timeline-actor"
              placeholder={t('exploration.timeline.actorPlaceholder')}
              value={inputs.actor}
              onChange={(e) => setInputs((s) => ({ ...s, actor: e.target.value }))}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="timeline-space">{t('exploration.timeline.spaceLabel')}</Label>
            <Input
              id="timeline-space"
              placeholder={t('exploration.timeline.spacePlaceholder')}
              value={inputs.space}
              onChange={(e) => setInputs((s) => ({ ...s, space: e.target.value }))}
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="timeline-event-type">{t('exploration.timeline.eventTypeLabel')}</Label>
            <Input
              id="timeline-event-type"
              placeholder={t('exploration.timeline.eventTypePlaceholder')}
              value={inputs.eventType}
              onChange={(e) => setInputs((s) => ({ ...s, eventType: e.target.value }))}
            />
          </div>
          <Button type="submit" variant="outline">
            {t('exploration.timeline.apply')}
          </Button>
          {hasActiveFilters && (
            <Button type="button" variant="ghost" onClick={onClear}>
              {t('exploration.timeline.clear')}
            </Button>
          )}
        </div>
      </form>

      {isError && (
        <div className="mb-4">
          <InlineBanner
            variant="error"
            message={t('exploration.timeline.loadError')}
            onDismiss={() => navigate(0)}
          />
        </div>
      )}

      {isLoading && <TimelineSkeleton />}

      {!isLoading && !isError && events.length === 0 && (
        <p className="text-sm text-muted-foreground">
          {hasActiveFilters ? t('exploration.timeline.noMatch') : t('exploration.timeline.empty')}
        </p>
      )}

      {!isLoading && !isError && events.length > 0 && (
        <>
          <div className="space-y-6">
            {groupBySession(events).map((group) => (
              <div key={group.sessionSequenceNumber}>
                <h2 className="mb-2 text-sm font-semibold text-foreground">
                  {t('exploration.timeline.sessionHeading', {
                    sequence: group.sessionSequenceNumber,
                  })}
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
              {isFetchingNextPage
                ? t('exploration.timeline.loadingMore')
                : t('exploration.timeline.loadMore')}
            </Button>
          </div>
        </>
      )}
    </section>
  )
}
