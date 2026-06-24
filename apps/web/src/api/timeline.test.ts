import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { apiClient } from './client'
import { getTimelinePage, timelineKeys, useTimeline } from './timeline'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import { useCampaignStore } from '@/store/campaignStore'
import type { TimelineEvent } from '@/types/timeline'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn() },
}))

function envelope<T>(data: T, meta: unknown = null) {
  return { data, meta, errors: [] }
}

function wrapper({ children }: { children: ReactNode }) {
  return createElement(QueryClientProvider, { client: createTestQueryClient() }, children)
}

const event: TimelineEvent = {
  eventId: 'e1',
  name: 'Ambush at the Pass',
  eventType: 'battle',
  involvedActorNames: ['Aldric', 'Seraphine'],
  spaceName: 'Mountain Pass',
  sessionId: 's1',
  sessionSequenceNumber: 1,
  createdAt: '2026-01-01T09:00:00Z',
}

beforeEach(() => {
  vi.clearAllMocks()
  useCampaignStore.getState().clearCampaign()
})

describe('timelineKeys', () => {
  it('scopes the list key under the campaign and the supplied filters', () => {
    expect(timelineKeys.all('c1')).toEqual(['timeline', 'c1'])
    expect(timelineKeys.list('c1', { eventType: 'battle' })).toEqual([
      'timeline',
      'c1',
      'list',
      { eventType: 'battle' },
    ])
    expect(timelineKeys.list('c1')).toEqual(['timeline', 'c1', 'list', {}])
  })
})

describe('getTimelinePage', () => {
  it('requests the first page with the limit and maps meta.nextCursor', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope({ events: [event] }, { nextCursor: 'cursor-xyz' })
    )

    const result = await getTimelinePage('c1', undefined)

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/timeline?limit=20')
    expect(result).toEqual({ events: [event], nextCursor: 'cursor-xyz' })
  })

  it('includes the cursor and filter params when provided', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope({ events: [] }, { nextCursor: null }))

    await getTimelinePage('c1', 'cursor-abc', {
      actor: 'ald',
      space: 'pass',
      eventType: 'battle',
    })

    expect(apiClient.get).toHaveBeenCalledWith(
      '/api/v1/campaigns/c1/timeline?cursor=cursor-abc&limit=20&actor=ald&space=pass&eventType=battle'
    )
  })

  it('defaults nextCursor to null when meta omits it', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope({ events: [] }))

    const result = await getTimelinePage('c1', undefined)

    expect(result.nextCursor).toBeNull()
  })
})

describe('useTimeline', () => {
  it('returns the first page once a campaign is active', async () => {
    useCampaignStore.getState().setCampaign('c1', 'player')
    vi.mocked(apiClient.get).mockResolvedValue(envelope({ events: [event] }, { nextCursor: null }))

    const { result } = renderHook(() => useTimeline(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.pages[0].events).toEqual([event])
    expect(result.current.hasNextPage).toBe(false)
  })

  it('does not fetch while there is no active campaign', () => {
    renderHook(() => useTimeline(), { wrapper })

    expect(apiClient.get).not.toHaveBeenCalled()
  })

  it('exposes a further page when the first page returns a cursor', async () => {
    useCampaignStore.getState().setCampaign('c1', 'gm')
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope({ events: [event] }, { nextCursor: 'cursor-xyz' })
    )

    const { result } = renderHook(() => useTimeline(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.hasNextPage).toBe(true)
  })
})
