import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { apiClient } from './client'
import {
  entityLinksKeys,
  getEntities,
  getEntityDetail,
  getEntityLinks,
  useAllEntities,
  useEntityDetail,
  useEntityLinks,
  useEntityList,
  worldstateKeys,
} from './worldstate'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import { useCampaignStore } from '@/store/campaignStore'
import type { EntityDetail, EntityLinks, EntitySummary } from '@/types/worldstate'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn() },
}))

function envelope<T>(data: T, meta: unknown = null) {
  return { data, meta, errors: [] }
}

function wrapper({ children }: { children: ReactNode }) {
  return createElement(QueryClientProvider, { client: createTestQueryClient() }, children)
}

const summary: EntitySummary = {
  entityId: 'a1',
  entityType: 'actor',
  name: 'Aldric',
  latestVersionNumber: 2,
  currentSnapshot: { role: 'knight' },
  lastUpdatedSessionId: 's2',
  createdAt: '2026-01-01T09:00:00Z',
}

const detail: EntityDetail = {
  entityId: 'a1',
  entityType: 'actor',
  name: 'Aldric',
  ownerId: 'u1',
  createdAt: '2026-01-01T09:00:00Z',
  versions: [
    {
      versionId: 'v1',
      versionNumber: 1,
      sessionId: 's1',
      sessionSequenceNumber: 1,
      changedFields: {},
      fullSnapshot: { role: 'squire' },
      createdAt: '2026-01-01T09:00:00Z',
    },
  ],
}

beforeEach(() => {
  vi.clearAllMocks()
  useCampaignStore.getState().clearCampaign()
})

describe('worldstateKeys', () => {
  it('scopes list and detail keys under the campaign + entity type', () => {
    expect(worldstateKeys.all('c1')).toEqual(['worldstate', 'c1'])
    expect(worldstateKeys.list('c1', 'actor', 0)).toEqual(['worldstate', 'c1', 'actor', 'list', 0])
    expect(worldstateKeys.detail('c1', 'space', 'x1')).toEqual([
      'worldstate',
      'c1',
      'space',
      'detail',
      'x1',
    ])
  })
})

describe('getEntities', () => {
  it('GETs the pluralised endpoint with paging params and maps meta into the page', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope([summary], { page: 0, size: 20, totalCount: 1 })
    )

    const result = await getEntities('c1', 'actor', 0)

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/actors?page=0&size=20')
    expect(result).toEqual({ items: [summary], page: 0, size: 20, totalCount: 1 })
  })

  it('pluralises the space entity type and requests the correct page', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope([], { page: 2, size: 20, totalCount: 0 }))

    await getEntities('c1', 'space', 2)

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/spaces?page=2&size=20')
  })

  it('passes a custom size when provided', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope([summary], { page: 0, size: 100, totalCount: 1 })
    )

    await getEntities('c1', 'actor', 0, 100)

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/actors?page=0&size=100')
  })
})

describe('getEntityDetail', () => {
  it('GETs the entity detail endpoint and unwraps the envelope', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope(detail))

    const result = await getEntityDetail('c1', 'actor', 'a1')

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/actors/a1')
    expect(result).toEqual(detail)
  })
})

describe('useEntityList', () => {
  it('returns the mapped page once a campaign is active', async () => {
    useCampaignStore.getState().setCampaign('c1', 'gm')
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope([summary], { page: 0, size: 20, totalCount: 1 })
    )

    const { result } = renderHook(() => useEntityList('actor', 0), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.items).toEqual([summary])
    expect(result.current.data?.totalCount).toBe(1)
  })

  it('does not fetch while there is no active campaign', () => {
    renderHook(() => useEntityList('actor', 0), { wrapper })

    expect(apiClient.get).not.toHaveBeenCalled()
  })
})

describe('useAllEntities', () => {
  it('fetches page 0 with size 100 and returns the items array directly (FU3)', async () => {
    useCampaignStore.getState().setCampaign('c1', 'gm')
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope([summary], { page: 0, size: 100, totalCount: 1 })
    )

    const { result } = renderHook(() => useAllEntities('actor'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([summary])
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/actors?page=0&size=100')
  })

  it('does not fetch while there is no active campaign', () => {
    renderHook(() => useAllEntities('actor'), { wrapper })

    expect(apiClient.get).not.toHaveBeenCalled()
  })
})

describe('useEntityDetail', () => {
  it('returns the entity detail on success', async () => {
    useCampaignStore.getState().setCampaign('c1', 'player')
    vi.mocked(apiClient.get).mockResolvedValue(envelope(detail))

    const { result } = renderHook(() => useEntityDetail('actor', 'a1'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(detail)
  })

  it('does not fetch when the entity id is empty', () => {
    useCampaignStore.getState().setCampaign('c1', 'player')

    renderHook(() => useEntityDetail('actor', ''), { wrapper })

    expect(apiClient.get).not.toHaveBeenCalled()
  })
})

const links: EntityLinks = {
  relations: [
    {
      relationId: 'r1',
      name: 'Aldric guards the Tavern',
      kind: 'guardianship',
      sourceEntityId: 'a1',
      sourceEntityType: 'actor',
      targetEntityId: 'x1',
      targetEntityType: 'space',
      sessionId: 's1',
    },
  ],
  relatedEntities: [
    {
      entityId: 'x1',
      entityType: 'space',
      name: 'The Tavern',
      latestVersionNumber: 1,
      currentSnapshot: {},
      lastUpdatedSessionId: 's1',
      createdAt: '2026-01-01T09:00:00Z',
    },
  ],
  events: [
    {
      eventId: 'e1',
      name: 'The Brawl',
      eventType: 'conflict',
      involvedActorNames: ['Aldric'],
      spaceName: 'The Tavern',
      sessionId: 's1',
      sessionSequenceNumber: 1,
      createdAt: '2026-01-01T09:00:00Z',
    },
  ],
  appearances: [
    {
      sessionId: 's1',
      status: 'COMMITTED',
      sequenceNumber: 1,
      committedAt: '2026-01-02T09:00:00Z',
      createdAt: '2026-01-01T09:00:00Z',
    },
    {
      sessionId: 's2',
      status: 'COMMITTED',
      sequenceNumber: 2,
      committedAt: '2026-01-04T09:00:00Z',
      createdAt: '2026-01-03T09:00:00Z',
    },
  ],
}

describe('entityLinksKeys', () => {
  it('nests the links key under the entity detail key', () => {
    expect(entityLinksKeys.links('c1', 'actor', 'a1')).toEqual([
      'worldstate',
      'c1',
      'actor',
      'detail',
      'a1',
      'links',
    ])
  })
})

describe('getEntityLinks', () => {
  it('GETs the pluralised links endpoint and unwraps the envelope', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope(links))

    const result = await getEntityLinks('c1', 'space', 'x1')

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/spaces/x1/links')
    expect(result).toEqual(links)
  })
})

describe('useEntityLinks', () => {
  it('returns the cross-link bundle once a campaign is active', async () => {
    useCampaignStore.getState().setCampaign('c1', 'player')
    vi.mocked(apiClient.get).mockResolvedValue(envelope(links))

    const { result } = renderHook(() => useEntityLinks('actor', 'a1'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(links)
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/actors/a1/links')
  })

  it('does not fetch while there is no active campaign', () => {
    renderHook(() => useEntityLinks('actor', 'a1'), { wrapper })

    expect(apiClient.get).not.toHaveBeenCalled()
  })

  it('does not fetch when the entity id is empty', () => {
    useCampaignStore.getState().setCampaign('c1', 'player')

    renderHook(() => useEntityLinks('actor', ''), { wrapper })

    expect(apiClient.get).not.toHaveBeenCalled()
  })
})
