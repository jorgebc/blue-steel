import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { apiClient, ApiClientError } from './client'
import { submitQuery, useSubmitQuery } from './queries'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import type { QueryResponse } from '@/types/query'

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

function envelope<T>(data: T) {
  return { data, meta: null, errors: [] }
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
