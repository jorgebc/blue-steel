import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { apiClient } from './client'
import { getRelations, getRelationDetail, relationKeys, useRelations } from './relations'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import { useCampaignStore } from '@/store/campaignStore'
import type { Relation } from '@/types/relation'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn() },
}))

function envelope<T>(data: T, meta: unknown = null) {
  return { data, meta, errors: [] }
}

function wrapper({ children }: { children: ReactNode }) {
  return createElement(QueryClientProvider, { client: createTestQueryClient() }, children)
}

const relation: Relation = {
  relationId: 'r1',
  name: 'Mira guides the party',
  kind: 'alliance',
  sourceEntityId: 'a1',
  sourceEntityType: 'actor',
  targetEntityId: 's1',
  targetEntityType: 'space',
  sessionId: 'sess1',
}

beforeEach(() => {
  vi.clearAllMocks()
  useCampaignStore.getState().clearCampaign()
})

describe('relationKeys', () => {
  it('scopes list and detail keys under the campaign', () => {
    expect(relationKeys.all('c1')).toEqual(['relations', 'c1'])
    expect(relationKeys.list('c1')).toEqual(['relations', 'c1', 'list'])
    expect(relationKeys.detail('c1', 'r1')).toEqual(['relations', 'c1', 'detail', 'r1'])
  })
})

describe('getRelations', () => {
  it('requests the campaign relations endpoint and returns the data array', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope([relation]))

    const result = await getRelations('c1')

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/relations')
    expect(result).toEqual([relation])
  })
})

describe('getRelationDetail', () => {
  it('requests the single-relation endpoint', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope({ ...relation, ownerId: 'u1', createdAt: '2026-01-01T00:00:00Z', versions: [] })
    )

    const result = await getRelationDetail('c1', 'r1')

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/relations/r1')
    expect(result.relationId).toBe('r1')
  })
})

describe('useRelations', () => {
  it('returns relations once a campaign is active', async () => {
    useCampaignStore.getState().setCampaign('c1', 'player')
    vi.mocked(apiClient.get).mockResolvedValue(envelope([relation]))

    const { result } = renderHook(() => useRelations(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([relation])
  })

  it('does not fetch while there is no active campaign', () => {
    renderHook(() => useRelations(), { wrapper })

    expect(apiClient.get).not.toHaveBeenCalled()
  })
})
