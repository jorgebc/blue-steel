import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import * as rq from '@tanstack/react-query'
import { QueryClientProvider, type QueryKey } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { apiClient, ApiClientError } from './client'
import {
  extractExistingSessionId,
  getSessionDiff,
  getSessionStatus,
  sessionKeys,
  submitSession,
  useSessionDiff,
  useSessionStatus,
  useSubmitSession,
} from './sessions'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import type { DiffPayload, SessionAcceptedResponse, SessionStatus } from '@/types/session'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn() },
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

// Spy on useQuery while delegating to the real implementation, so the hooks work
// normally and we can still inspect the options passed (e.g. refetchInterval).
vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>()
  return { ...actual, useQuery: vi.fn(actual.useQuery) }
})

const accepted: SessionAcceptedResponse = { sessionId: 's1', status: 'PENDING' }

function envelope<T>(data: T) {
  return { data, meta: null, errors: [] }
}

function wrapper({ children }: { children: ReactNode }) {
  return createElement(QueryClientProvider, { client: createTestQueryClient() }, children)
}

/** Builds a minimal query stub carrying just the status the predicate reads. */
function queryWithStatus(status: SessionStatus) {
  return { state: { data: { status } } }
}

beforeEach(() => {
  vi.clearAllMocks()
})

const diffPayload: DiffPayload = {
  narrativeSummaryHeader: 'The party reached Barovia.',
  actors: [],
  spaces: [],
  events: [],
  relations: [],
  detectedConflicts: [],
}

describe('sessionKeys', () => {
  it('scopes the status and diff keys under the campaign session list key', () => {
    expect(sessionKeys.all('c1')).toEqual(['sessions', 'c1'])
    expect(sessionKeys.status('c1', 's1')).toEqual(['sessions', 'c1', 's1', 'status'])
    expect(sessionKeys.diff('c1', 's1')).toEqual(['sessions', 'c1', 's1', 'diff'])
  })
})

describe('submitSession', () => {
  it('POSTs summaryText to the campaign sessions endpoint and unwraps the envelope', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope(accepted))

    const result = await submitSession('c1', { summaryText: 'The party fought a dragon.' })

    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/campaigns/c1/sessions', {
      summaryText: 'The party fought a dragon.',
    })
    expect(result).toEqual(accepted)
  })
})

describe('getSessionStatus', () => {
  it('GETs the status endpoint and unwraps the envelope', async () => {
    const status = { sessionId: 's1', status: 'PROCESSING', failureReason: null, message: null }
    vi.mocked(apiClient.get).mockResolvedValue(envelope(status))

    const result = await getSessionStatus('c1', 's1')

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/sessions/s1/status')
    expect(result).toEqual(status)
  })
})

describe('getSessionDiff', () => {
  it('GETs the diff endpoint and unwraps the envelope', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope(diffPayload))

    const result = await getSessionDiff('c1', 's1')

    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/sessions/s1/diff')
    expect(result).toEqual(diffPayload)
  })

  it('propagates the error when the draft is missing (404)', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(
      new ApiClientError('not found', 404, [
        { code: 'SESSION_NOT_FOUND', message: 'No draft', field: null },
      ])
    )

    await expect(getSessionDiff('c1', 's1')).rejects.toBeInstanceOf(ApiClientError)
  })
})

describe('useSessionDiff', () => {
  it('returns the diff payload on success', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope(diffPayload))

    const { result } = renderHook(() => useSessionDiff('c1', 's1'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiClient.get).toHaveBeenCalledWith('/api/v1/campaigns/c1/sessions/s1/diff')
    expect(result.current.data).toEqual(diffPayload)
  })

  it('surfaces the error state when the diff request rejects', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useSessionDiff('c1', 's1'), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })

  it('does not fetch when disabled', () => {
    renderHook(() => useSessionDiff('c1', 's1', false), { wrapper })

    expect(apiClient.get).not.toHaveBeenCalled()
  })
})

describe('useSubmitSession', () => {
  it('invalidates the campaign session cache on success', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope(accepted))
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const localWrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children)

    const { result } = renderHook(() => useSubmitSession('c1'), { wrapper: localWrapper })
    result.current.mutate({ summaryText: 'hello' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: sessionKeys.all('c1') })
  })
})

describe('useSessionStatus refetchInterval', () => {
  function capturedRefetchInterval() {
    vi.mocked(apiClient.get).mockResolvedValue(
      envelope({ sessionId: 's1', status: 'PROCESSING', failureReason: null, message: null })
    )
    renderHook(() => useSessionStatus('c1', 's1', true), { wrapper })
    const calls = vi.mocked(rq.useQuery).mock.calls
    const options = calls[calls.length - 1][0] as {
      refetchInterval: (query: { state: { data?: { status: SessionStatus } } }) => number | false
      queryKey: QueryKey
    }
    return options.refetchInterval
  }

  it('polls every 2000ms while the session is PROCESSING', () => {
    const refetchInterval = capturedRefetchInterval()
    expect(refetchInterval(queryWithStatus('PROCESSING'))).toBe(2000)
  })

  it('stops polling once the session reaches a terminal state', () => {
    const refetchInterval = capturedRefetchInterval()
    expect(refetchInterval(queryWithStatus('DRAFT'))).toBe(false)
    expect(refetchInterval(queryWithStatus('FAILED'))).toBe(false)
  })

  it('does not fetch when disabled', () => {
    renderHook(() => useSessionStatus('c1', 's1', false), { wrapper })
    expect(apiClient.get).not.toHaveBeenCalled()
  })
})

describe('extractExistingSessionId', () => {
  const uuid = '3f8a1c2e-9b4d-4f6a-8c1e-2d3b4a5c6d7e'

  it('returns the UUID embedded in an ACTIVE_SESSION_EXISTS message', () => {
    const error = new ApiClientError('conflict', 409, [
      {
        code: 'ACTIVE_SESSION_EXISTS',
        message: `An active session already exists: ${uuid}`,
        field: null,
      },
    ])

    expect(extractExistingSessionId(error)).toBe(uuid)
  })

  it('returns null when the message carries no UUID', () => {
    const error = new ApiClientError('conflict', 409, [
      { code: 'ACTIVE_SESSION_EXISTS', message: 'An active session already exists', field: null },
    ])

    expect(extractExistingSessionId(error)).toBeNull()
  })

  it('returns null for a non-conflict error', () => {
    const error = new ApiClientError('boom', 400, [
      { code: 'VALIDATION_ERROR', message: uuid, field: 'summaryText' },
    ])

    expect(extractExistingSessionId(error)).toBeNull()
  })

  it('returns null for a non-ApiClientError value', () => {
    expect(extractExistingSessionId(new Error(uuid))).toBeNull()
  })
})
