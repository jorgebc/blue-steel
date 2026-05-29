import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { apiClient } from './client'
import {
  campaignKeys,
  createCampaign,
  getCampaign,
  getCampaigns,
  useCampaign,
  useCampaigns,
  useCreateCampaign,
} from './campaigns'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import type { CampaignResponse } from '@/types/campaign'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}))

const campaign: CampaignResponse = {
  id: 'c1',
  name: 'Curse of Strahd',
  createdBy: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  role: 'gm',
}

function envelope<T>(data: T) {
  return { data, meta: null, errors: [] }
}

function wrapper({ children }: { children: ReactNode }) {
  return createElement(QueryClientProvider, { client: createTestQueryClient() }, children)
}

describe('campaigns API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getCampaigns() GETs the campaigns endpoint and unwraps the envelope', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope([campaign]))

    const result = await getCampaigns()

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns')
    expect(result).toEqual([campaign])
  })

  it('getCampaign() GETs the campaign-by-id endpoint and unwraps the envelope', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope(campaign))

    const result = await getCampaign('c1')

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1')
    expect(result).toEqual(campaign)
  })

  it('getCampaign() propagates the error when the request rejects (403/404)', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error('forbidden'))

    await expect(getCampaign('c1')).rejects.toThrow('forbidden')
  })
})

describe('useCampaigns', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns the list of campaigns on success', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope([campaign]))

    const { result } = renderHook(() => useCampaigns(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([campaign])
  })

  it('surfaces the error state when the list request rejects', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useCampaigns(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useCampaign', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('returns a single campaign on success', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope(campaign))

    const { result } = renderHook(() => useCampaign('c1'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1')
    expect(result.current.data).toEqual(campaign)
  })

  it('does not fetch when id is undefined', () => {
    const { result } = renderHook(() => useCampaign(undefined), { wrapper })

    expect(apiClient.get).not.toHaveBeenCalled()
    expect(result.current.fetchStatus).toBe('idle')
  })
})

describe('createCampaign', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('POSTs name and gmUserId to the campaigns endpoint and unwraps the envelope', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope(campaign))

    const result = await createCampaign({ name: 'Curse of Strahd', gmUserId: 'u1' })

    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/campaigns', {
      name: 'Curse of Strahd',
      gmUserId: 'u1',
    })
    expect(result).toEqual(campaign)
  })
})

describe('useCreateCampaign', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('invalidates the campaign list cache on success', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope(campaign))
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const localWrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children)

    const { result } = renderHook(() => useCreateCampaign(), { wrapper: localWrapper })
    result.current.mutate({ name: 'Curse of Strahd', gmUserId: 'u1' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: campaignKeys.all })
  })
})
