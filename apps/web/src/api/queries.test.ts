import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { apiClient, ApiClientError } from './client'
import {
  fetchQueryHistory,
  fetchQueryUsage,
  submitQuery,
  useQueryHistory,
  useSubmitQuery,
} from './queries'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import type { QueryHistoryEntry, QueryResponse, QueryUsage } from '@/types/query'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
  ApiClientError: class ApiClientError extends Error {
    constructor(
      message: string,
      public readonly status: number,
      public readonly errors: { code: string; message: string; field: string | null }[]
    ) {
      super(message)
      this.name = 'ApiClientError'
    }
  },
}))

function envelope<T>(data: T, meta: unknown = null) {
  return { data, meta, errors: [] }
}

function wrapper({ children }: { children: ReactNode }) {
  return createElement(QueryClientProvider, { client: createTestQueryClient() }, children)
}

const answer: QueryResponse = {
  answer: 'Aldric fled north after the battle.',
  citations: [
    { sessionId: 's1', sequenceNumber: 3, snippet: 'Aldric was last seen heading north.' },
  ],
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('submitQuery', () => {
  it('POSTs the question to the campaign queries endpoint and unwraps the envelope', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope(answer))

    const result = await submitQuery('c1', 'Where did Aldric go?')

    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/campaigns/c1/queries', {
      question: 'Where did Aldric go?',
    })
    expect(result).toEqual(answer)
  })

  it('propagates a 504 QUERY_TIMEOUT so the caller can branch on the status', async () => {
    vi.mocked(apiClient.post).mockRejectedValue(
      new ApiClientError('timed out', 504, [
        { code: 'QUERY_TIMEOUT', message: 'The query timed out', field: null },
      ])
    )

    await expect(submitQuery('c1', 'slow question')).rejects.toMatchObject({
      status: 504,
    })
  })
})

describe('fetchQueryUsage', () => {
  const usage: QueryUsage = {
    consumedUsd: 0.25,
    capUsd: 1.0,
    requestsRemaining: 8,
    maxRequests: 10,
    windowSeconds: 60,
  }

  it('GETs the usage endpoint for the campaign and unwraps the envelope', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope(usage))

    const result = await fetchQueryUsage('c1')

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/queries/usage')
    expect(result).toEqual(usage)
  })
})

describe('fetchQueryHistory', () => {
  const entries: QueryHistoryEntry[] = [
    {
      id: 'q1',
      question: 'Where did Aldric go?',
      answer: 'Aldric fled north after the battle.',
      citations: [
        { sessionId: 's1', sequenceNumber: 3, snippet: 'Aldric was last seen heading north.' },
      ],
      createdAt: '2026-06-18T10:00:00Z',
    },
  ]

  it('GETs the paginated history endpoint and maps the envelope meta', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope(entries, { page: 1, size: 20, totalCount: 25 })
    )

    const result = await fetchQueryHistory('c1', 1)

    expect(apiClient.get).toHaveBeenCalledWith(
      '/api/v1/campaigns/c1/queries/history?page=1&size=20'
    )
    expect(result).toEqual({ items: entries, page: 1, size: 20, totalCount: 25 })
  })

  it('falls back to the requested page and item count when meta is absent', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope(entries))

    const result = await fetchQueryHistory('c1', 0)

    expect(result).toEqual({ items: entries, page: 0, size: 20, totalCount: 1 })
  })
})

describe('useQueryHistory', () => {
  const entries: QueryHistoryEntry[] = [
    {
      id: 'q1',
      question: 'Where did Aldric go?',
      answer: 'Aldric fled north after the battle.',
      citations: [],
      createdAt: '2026-06-18T10:00:00Z',
    },
  ]

  it('fetches the page once a campaign is selected', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope(entries, { page: 0, size: 20, totalCount: 1 })
    )

    const { result } = renderHook(() => useQueryHistory('c1', 0), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual({ items: entries, page: 0, size: 20, totalCount: 1 })
  })

  it('stays disabled until a campaign is selected', () => {
    const { result } = renderHook(() => useQueryHistory('', 0), { wrapper })

    expect(result.current.fetchStatus).toBe('idle')
    expect(apiClient.get).not.toHaveBeenCalled()
  })
})

describe('useSubmitQuery', () => {
  it('returns the answer on a successful mutation', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope(answer))

    const { result } = renderHook(() => useSubmitQuery('c1'), { wrapper })
    result.current.mutate('Where did Aldric go?')

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(answer)
  })

  it('surfaces the 504 timeout as the mutation error', async () => {
    vi.mocked(apiClient.post).mockRejectedValue(
      new ApiClientError('timed out', 504, [
        { code: 'QUERY_TIMEOUT', message: 'The query timed out', field: null },
      ])
    )

    const { result } = renderHook(() => useSubmitQuery('c1'), { wrapper })
    result.current.mutate('slow question')

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBeInstanceOf(ApiClientError)
    expect((result.current.error as ApiClientError).status).toBe(504)
  })
})
