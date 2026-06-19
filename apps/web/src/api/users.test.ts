import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import React from 'react'
import {
  changePassword,
  searchUsers,
  updateProfile,
  useChangePassword,
  useUpdateProfile,
  useUserSearch,
} from './users'
import { ApiClientError } from './client'
import { useAuthStore } from '@/store/authStore'
import { useSettingsStore } from '@/store/settingsStore'
import type { ApiEnvelope } from '@/types/api'
import type { UserMeResponse } from '@/types/auth'

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
      (err: unknown) => err instanceof ApiClientError && err.errors[0].code === 'INCORRECT_PASSWORD'
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

  // ── updateProfile() ────────────────────────────────────────────────────────────

  it('updateProfile() makes PATCH to /api/v1/users/me with the supplied payload', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse({}))

    await updateProfile({ theme: 'dark', uiLocale: 'es' })

    const [url, init] = fetchSpy.mock.calls[0]
    expect(url).toBe(`${BASE}/api/v1/users/me`)
    expect((init as RequestInit).method).toBe('PATCH')
    expect((init as RequestInit).body).toBe(JSON.stringify({ theme: 'dark', uiLocale: 'es' }))
  })

  // ── useUpdateProfile ───────────────────────────────────────────────────────────

  it('useUpdateProfile on success: refetches /me and updates authStore + settingsStore', async () => {
    const refreshedUser: UserMeResponse = {
      id: 'u1',
      email: 'gm@example.com',
      isAdmin: false,
      forcePasswordChange: false,
      displayName: 'Game Master',
      avatarAccentColor: '#3b82f6',
      uiLocale: 'es',
      theme: 'dark',
    }
    useAuthStore.setState({ currentUser: null })
    useSettingsStore.setState({ theme: 'system', uiLocale: 'en' })
    fetchSpy
      .mockResolvedValueOnce(envelopeResponse({})) // PATCH /users/me
      .mockResolvedValueOnce(envelopeResponse(refreshedUser)) // GET /users/me

    const { result } = renderHook(() => useUpdateProfile(), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync({ theme: 'dark', uiLocale: 'es' })
    })

    await waitFor(() => {
      expect(useAuthStore.getState().currentUser).toEqual(refreshedUser)
      expect(useSettingsStore.getState().theme).toBe('dark')
      expect(useSettingsStore.getState().uiLocale).toBe('es')
    })
  })

  // ── searchUsers() ─────────────────────────────────────────────────────────────

  it('searchUsers() GETs /api/v1/users with an encoded email query and unwraps the envelope', async () => {
    const users = [{ id: 'u1', email: 'alice@example.com' }]
    fetchSpy.mockResolvedValueOnce(envelopeResponse(users))

    const result = await searchUsers('alice@example.com')

    const [url, init] = fetchSpy.mock.calls[0]
    expect(url).toBe(`${BASE}/api/v1/users?email=alice%40example.com`)
    expect((init as RequestInit).method).toBe('GET')
    expect(result).toEqual(users)
  })

  // ── useUserSearch ────────────────────────────────────────────────────────────

  it('useUserSearch returns matching users on success', async () => {
    const users = [{ id: 'u1', email: 'alice@example.com' }]
    fetchSpy.mockResolvedValueOnce(envelopeResponse(users))

    const { result } = renderHook(() => useUserSearch('alice'), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(users)
  })

  it('useUserSearch does not fetch when the email query is empty', () => {
    const { result } = renderHook(() => useUserSearch(''), { wrapper: createWrapper() })

    expect(fetchSpy).not.toHaveBeenCalled()
    expect(result.current.fetchStatus).toBe('idle')
  })

  it('useUserSearch does not fetch when explicitly disabled', () => {
    const { result } = renderHook(() => useUserSearch('alice', false), {
      wrapper: createWrapper(),
    })

    expect(fetchSpy).not.toHaveBeenCalled()
    expect(result.current.fetchStatus).toBe('idle')
  })
})
