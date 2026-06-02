import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { apiClient } from './client'
import {
  getEntities,
  getEntityDetail,
  useEntityDetail,
  useEntityList,
  worldstateKeys,
} from './worldstate'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import { useCampaignStore } from '@/store/campaignStore'
import type { EntityDetail, EntitySummary } from '@/types/worldstate'

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
