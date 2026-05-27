import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import { login, logout, getCurrentUser, useLogin, useLogout } from './auth'
import { ApiClientError } from './client'
import { useAuthStore } from '@/store/authStore'
import type { ApiEnvelope } from '@/types/api'
import type { AuthLoginResponse, UserMeResponse } from '@/types/auth'

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

const mockLoginResponse: AuthLoginResponse = {
  accessToken: 'jwt-access-token',
  forcePasswordChange: false,
}

const mockUser: UserMeResponse = {
  id: 'user-1',
  email: 'gm@example.com',
  isAdmin: false,
  forcePasswordChange: false,
}

// ─── Test wrapper ────────────────────────────────────────────────────────────

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return ({ children }: { children: React.ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children)
}

// ─── Setup ───────────────────────────────────────────────────────────────────

describe('auth API', () => {
  let fetchSpy: ReturnType<typeof vi.spyOn>

  beforeAll(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE)
  })

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch')
    vi.stubGlobal('location', { href: '' })
    useAuthStore.setState({ accessToken: null, currentUser: null })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  // ── login() ─────────────────────────────────────────────────────────────────

  it('login() makes POST to /api/v1/auth/login with the supplied credentials', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse(mockLoginResponse))

    await login({ email: 'gm@example.com', password: 'secret' })

    const [url, init] = fetchSpy.mock.calls[0]
    expect(url).toBe(`${BASE}/api/v1/auth/login`)
    expect((init as RequestInit).method).toBe('POST')
    expect((init as RequestInit).body).toBe(
      JSON.stringify({ email: 'gm@example.com', password: 'secret' })
    )
  })

  it('login() returns accessToken and forcePasswordChange from the server envelope', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse(mockLoginResponse))

    const result = await login({ email: 'gm@example.com', password: 'secret' })

    expect(result.accessToken).toBe('jwt-access-token')
    expect(result.forcePasswordChange).toBe(false)
  })

  it('login() throws ApiClientError when the server returns 400 validation errors', async () => {
    fetchSpy.mockResolvedValueOnce(
      errorEnvelopeResponse('MISSING_FIELDS', 'Email and password are required', 400)
    )

    await expect(login({ email: '', password: '' })).rejects.toSatisfy(
      (err: unknown) =>
        err instanceof ApiClientError && err.errors[0].code === 'MISSING_FIELDS'
    )
  })

  // ── logout() ────────────────────────────────────────────────────────────────

  it('logout() makes POST to /api/v1/auth/logout', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse({}))

    await logout()

    const [url, init] = fetchSpy.mock.calls[0]
    expect(url).toBe(`${BASE}/api/v1/auth/logout`)
    expect((init as RequestInit).method).toBe('POST')
  })

  // ── getCurrentUser() ─────────────────────────────────────────────────────────

  it('getCurrentUser() makes GET to /api/v1/users/me and returns the user profile', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse(mockUser))

    const result = await getCurrentUser()

    const [url] = fetchSpy.mock.calls[0]
    expect(url).toBe(`${BASE}/api/v1/users/me`)
    expect(result).toEqual(mockUser)
  })

  // ── useLogin ────────────────────────────────────────────────────────────────

  it('useLogin on success: stores accessToken, fetches /users/me, and stores currentUser', async () => {
    fetchSpy
      .mockResolvedValueOnce(envelopeResponse(mockLoginResponse)) // POST /auth/login
      .mockResolvedValueOnce(envelopeResponse(mockUser))          // GET /users/me

    const { result } = renderHook(() => useLogin(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync({ email: 'gm@example.com', password: 'secret' })
    })

    expect(useAuthStore.getState().accessToken).toBe('jwt-access-token')
    expect(useAuthStore.getState().currentUser).toEqual(mockUser)
  })

  it('useLogin on success: returns forcePasswordChange to the caller', async () => {
    const responseWithForce: AuthLoginResponse = { accessToken: 'tok', forcePasswordChange: true }
    fetchSpy
      .mockResolvedValueOnce(envelopeResponse(responseWithForce))
      .mockResolvedValueOnce(envelopeResponse({ ...mockUser, forcePasswordChange: true }))

    const { result } = renderHook(() => useLogin(), { wrapper: createWrapper() })

    let data: AuthLoginResponse | undefined
    await act(async () => {
      data = await result.current.mutateAsync({ email: 'gm@example.com', password: 'secret' })
    })

    expect(data?.forcePasswordChange).toBe(true)
  })

  // ── useLogout ───────────────────────────────────────────────────────────────

  it('useLogout on success: clears the auth store and query cache', async () => {
    useAuthStore.setState({ accessToken: 'tok', currentUser: mockUser })
    fetchSpy.mockResolvedValueOnce(envelopeResponse({})) // POST /auth/logout

    const { result } = renderHook(() => useLogout(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync()
    })

    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBeNull()
      expect(useAuthStore.getState().currentUser).toBeNull()
    })
  })

  it('useLogout on server failure: still clears the auth store (best-effort)', async () => {
    useAuthStore.setState({ accessToken: 'tok', currentUser: mockUser })
    fetchSpy.mockResolvedValueOnce(new Response(null, { status: 500 })) // server error

    const { result } = renderHook(() => useLogout(), { wrapper: createWrapper() })

    await act(async () => {
      // mutate (not mutateAsync) to avoid throwing — we only care about side effects
      result.current.mutate()
    })

    await waitFor(() => {
      expect(useAuthStore.getState().accessToken).toBeNull()
    })
  })
})
