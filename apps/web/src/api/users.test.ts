import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { changePassword, useChangePassword } from './users'
import { ApiClientError } from './client'
import type { ApiEnvelope } from '@/types/api'

const BASE = 'http://localhost:8080'

// ─── Response helpers ───────────────────────────────────────────────────────

function envelopeResponse<T>(data: T, status = 200): Response {
  return new Response(JSON.stringify({ data, meta: null, errors: [] } satisfies ApiEnvelope<T>), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function errorEnvelopeResponse(code: string, message: string, status: number): Response {
  return new Response(
    JSON.stringify({ data: null, meta: null, errors: [{ code, message, field: null }] }),
    { status, headers: { 'Content-Type': 'application/json' } }
  )
}

// ─── Test wrapper ─────────────────────────────────────────────────────────────

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children)
}

// ─── Tests ───────────────────────────────────────────────────────────────────

describe('users API', () => {
  let fetchSpy: ReturnType<typeof vi.spyOn>

  beforeAll(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE)
  })

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch')
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  // ── changePassword() ─────────────────────────────────────────────────────────

  it('changePassword() makes PATCH to /api/v1/users/me/password with current and new passwords', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse({}))

    await changePassword({ currentPassword: 'old-pass', newPassword: 'new-pass' })

    const [url, init] = fetchSpy.mock.calls[0]
    expect(url).toBe(`${BASE}/api/v1/users/me/password`)
    expect((init as RequestInit).method).toBe('PATCH')
    expect((init as RequestInit).body).toBe(
      JSON.stringify({ currentPassword: 'old-pass', newPassword: 'new-pass' })
    )
  })

  it('changePassword() throws ApiClientError when server returns 400', async () => {
    fetchSpy.mockResolvedValueOnce(
      errorEnvelopeResponse('INCORRECT_PASSWORD', 'Current password is incorrect', 400)
    )

    await expect(
      changePassword({ currentPassword: 'wrong', newPassword: 'new-pass' })
    ).rejects.toSatisfy(
      (err: unknown) =>
        err instanceof ApiClientError && err.errors[0].code === 'INCORRECT_PASSWORD'
    )
  })

  // ── useChangePassword ────────────────────────────────────────────────────────

  it('useChangePassword on success: resolves without error', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse({}))

    const { result } = renderHook(() => useChangePassword(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync({ currentPassword: 'old', newPassword: 'new' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })
})
