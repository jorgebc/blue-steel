import { useInfiniteQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import { useCampaignStore } from '@/store/campaignStore'
import type { TimelineEvent, TimelinePage } from '@/types/timeline'

const PAGE_SIZE = 20

/** Optional, all-nullable feed filters mirroring the backend `TimelineFilter`. */
export interface TimelineFilters {
  actor?: string
  space?: string
  eventType?: string
}

/** Query-key factory for the keyset-paginated timeline feed, scoped per campaign + filters. */
export const timelineKeys = {
  all: (campaignId: string) => ['timeline', campaignId] as const,
  list: (campaignId: string, filters?: TimelineFilters) =>
    [...timelineKeys.all(campaignId), 'list', filters ?? {}] as const,
}

/**
 * Fetches one keyset page of the event feed. `cursor` is `undefined` for the first page or the
 * previous page's `nextCursor`; the response `meta.nextCursor` drives continuation (D-055).
 */
export async function getTimelinePage(
  campaignId: string,
  cursor: string | undefined,
  filters?: TimelineFilters
): Promise<TimelinePage> {
  const params = new URLSearchParams()
  if (cursor) params.set('cursor', cursor)
  params.set('limit', String(PAGE_SIZE))
  if (filters?.actor) params.set('actor', filters.actor)
  if (filters?.space) params.set('space', filters.space)
  if (filters?.eventType) params.set('eventType', filters.eventType)

  const res = await apiClient.get<{ events: TimelineEvent[] }>(
    `/api/v1/campaigns/${campaignId}/timeline?${params.toString()}`
  )
  const meta = (res.meta ?? {}) as { nextCursor?: string | null }
  return {
    events: res.data.events,
    nextCursor: meta.nextCursor ?? null,
  }
}

/**
 * Keyset-paginated timeline feed for the active campaign. Changing `filters` swaps the query key so
 * TanStack resets the feed to the first page automatically.
 */
export function useTimeline(filters?: TimelineFilters) {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  return useInfiniteQuery({
    queryKey: timelineKeys.list(campaignId ?? '', filters),
    queryFn: ({ pageParam }) =>
      getTimelinePage(campaignId ?? '', pageParam as string | undefined, filters),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last: TimelinePage) => last.nextCursor ?? undefined,
    enabled: campaignId !== null,
  })
}
