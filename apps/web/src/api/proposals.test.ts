import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { apiClient } from './client'
import {
  proposalKeys,
  getProposals,
  createProposal,
  coSignProposal,
  decideProposal,
  useProposals,
  useCosignedProposals,
  useCreateProposal,
  useCoSignProposal,
  useDecideProposal,
} from './proposals'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import type { CreateProposalRequest, Proposal } from '@/types/proposal'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
}))

function envelope<T>(data: T, meta: unknown = null) {
  return { data, meta, errors: [] }
}

function wrapper({ children }: { children: ReactNode }) {
  return createElement(QueryClientProvider, { client: createTestQueryClient() }, children)
}

const proposal: Proposal = {
  proposalId: 'p1',
  campaignId: 'c1',
  targetType: 'ACTOR',
  targetId: 'e1',
  ownerId: 'u1',
  status: 'OPEN',
  proposedDelta: { description: 'A reformed thief.' },
  sessionId: 's1',
  resultingEntityVersionId: null,
  expiresAt: '2026-07-14T10:00:00Z',
  createdAt: '2026-06-14T10:00:00Z',
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('proposalKeys', () => {
  it('scopes the list key under the campaign proposals key', () => {
    expect(proposalKeys.all('c1')).toEqual(['proposals', 'c1'])
    expect(proposalKeys.list('c1', 'COSIGNED', 0)).toEqual([
      'proposals',
      'c1',
      'list',
      { status: 'COSIGNED', page: 0 },
    ])
    expect(proposalKeys.list('c1', undefined, 1)).toEqual([
      'proposals',
      'c1',
      'list',
      { status: null, page: 1 },
    ])
  })
})

describe('getProposals', () => {
  it('requests the paginated list and reads meta paging', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope([proposal], { page: 0, size: 20, totalCount: 1 })
    )

    const result = await getProposals('c1')

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/proposals?page=0&size=20')
    expect(result).toEqual({ proposals: [proposal], page: 0, size: 20, totalCount: 1 })
  })

  it('includes the status filter when provided', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope([]))

    await getProposals('c1', 'COSIGNED', 2)

    expect(apiClient.get).toHaveBeenCalledWith(
      '/api/v1/campaigns/c1/proposals?status=COSIGNED&page=2&size=20'
    )
  })
})

describe('createProposal', () => {
  it('posts the create body and returns the created proposal', async () => {
    const body: CreateProposalRequest = {
      targetType: 'ACTOR',
      targetId: 'e1',
      sessionId: 's1',
      proposedDelta: { description: 'A reformed thief.' },
    }
    vi.mocked(apiClient.post).mockResolvedValue(envelope(proposal))

    const result = await createProposal('c1', body)

    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/campaigns/c1/proposals', body)
    expect(result).toEqual(proposal)
  })
})

describe('coSignProposal', () => {
  it('posts to the votes endpoint with no body', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope({ ...proposal, status: 'COSIGNED' }))

    const result = await coSignProposal('c1', 'p1')

    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/campaigns/c1/proposals/p1/votes')
    expect(result.status).toBe('COSIGNED')
  })
})

describe('decideProposal', () => {
  it('posts the decision body and returns the resulting version id', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope({ resultingEntityVersionId: 'v9' }))

    const result = await decideProposal('c1', 'p1', { decision: 'APPROVE', editedDelta: null })

    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/campaigns/c1/proposals/p1/decision', {
      decision: 'APPROVE',
      editedDelta: null,
    })
    expect(result).toEqual({ resultingEntityVersionId: 'v9' })
  })
})

describe('useProposals', () => {
  it('returns the proposal page on success', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope([proposal], { page: 0, size: 20, totalCount: 1 })
    )

    const { result } = renderHook(() => useProposals('c1'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.proposals).toEqual([proposal])
  })

  it('does not fetch when the campaign id is empty', () => {
    renderHook(() => useProposals(''), { wrapper })

    expect(apiClient.get).not.toHaveBeenCalled()
  })
})

describe('useCosignedProposals', () => {
  it('requests the list filtered to COSIGNED', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope([]))

    const { result } = renderHook(() => useCosignedProposals('c1'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiClient.get).toHaveBeenCalledWith(
      '/api/v1/campaigns/c1/proposals?status=COSIGNED&page=0&size=20'
    )
  })
})

describe('useCreateProposal', () => {
  it('invalidates the campaign proposals on success', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope(proposal))
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const localWrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children)

    const { result } = renderHook(() => useCreateProposal('c1'), { wrapper: localWrapper })
    result.current.mutate({
      targetType: 'ACTOR',
      targetId: 'e1',
      sessionId: 's1',
      proposedDelta: { description: 'x' },
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: proposalKeys.all('c1') })
  })
})

describe('useCoSignProposal', () => {
  it('invalidates the campaign proposals on success', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope({ ...proposal, status: 'COSIGNED' }))
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const localWrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children)

    const { result } = renderHook(() => useCoSignProposal('c1'), { wrapper: localWrapper })
    result.current.mutate('p1')

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: proposalKeys.all('c1') })
  })
})

describe('useDecideProposal', () => {
  it('invalidates the campaign proposals on success', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope({ resultingEntityVersionId: 'v9' }))
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const localWrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children)

    const { result } = renderHook(() => useDecideProposal('c1'), { wrapper: localWrapper })
    result.current.mutate({ proposalId: 'p1', body: { decision: 'REJECT' } })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: proposalKeys.all('c1') })
  })
})
