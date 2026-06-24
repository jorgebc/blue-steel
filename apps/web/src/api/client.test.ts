import { describe, it, expect, vi, beforeAll, beforeEach, afterEach } from 'vitest'
import { apiClient, ApiClientError } from './client'
import { useAuthStore } from '@/store/authStore'
import type { ApiEnvelope } from '@/types/api'

const BASE = 'http://localhost:8080'

// Helper: build a Response carrying an API envelope
function envelopeResponse<T>(data: T, status = 200): Response {
  return new Response(JSON.stringify({ data, meta: null, errors: [] }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

// Helper: build a 401 response (no body parsing needed)
function unauthorizedResponse(): Response {
  return new Response(null, { status: 401 })
}

// Helper: build a response whose envelope carries errors
function errorEnvelopeResponse(code: string, message: string, status = 422): Response {
  const body: ApiEnvelope<null> = {
    data: null,
    meta: null,
    errors: [{ code, message, field: null }],
  }
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

// Helper: build a refresh-success response
function refreshSuccessResponse(newToken: string): Response {
  return new Response(JSON.stringify({ data: { accessToken: newToken }, meta: null, errors: [] }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiClient', () => {
  let fetchSpy: ReturnType<typeof vi.spyOn>
  let mockLocation: { href: string }

  beforeAll(() => {
    vi.stubEnv('VITE_API_BASE_URL', BASE)
  })

  beforeEach(() => {
    fetchSpy = vi.spyOn(globalThis, 'fetch')
    mockLocation = { href: '' }
    vi.stubGlobal('location', mockLocation)
    useAuthStore.setState({ accessToken: null, currentUser: null })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  // ── Auth header ────────────────────────────────────────────────────────────

  it('attaches Authorization: Bearer header when an access token is stored', async () => {
    useAuthStore.setState({ accessToken: 'tok-xyz' })
    fetchSpy.mockResolvedValueOnce(envelopeResponse({ id: '1' }))

    await apiClient.get('/api/v1/test')

    const [, init] = fetchSpy.mock.calls[0]
    expect((init as RequestInit).headers).toMatchObject({
      Authorization: 'Bearer tok-xyz',
    })
  })

  it('omits the Authorization header when no access token is stored', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse({ id: '1' }))

    await apiClient.get('/api/v1/test')

    const [, init] = fetchSpy.mock.calls[0]
    expect((init as RequestInit).headers).not.toHaveProperty('Authorization')
  })

  // ── Credentials ────────────────────────────────────────────────────────────

  it('always sends credentials: include', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse({}))

    await apiClient.get('/api/v1/test')

    const [, init] = fetchSpy.mock.calls[0]
    expect((init as RequestInit).credentials).toBe('include')
  })

  // ── Base URL ───────────────────────────────────────────────────────────────

  it('prepends VITE_API_BASE_URL to the request path', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse({}))

    await apiClient.get('/api/v1/campaigns')

    const [url] = fetchSpy.mock.calls[0]
    expect(url).toBe(`${BASE}/api/v1/campaigns`)
  })

  // ── HTTP methods ───────────────────────────────────────────────────────────

  it('POST serialises the body to JSON and sets Content-Type', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse({ sessionId: 's1' }))

    await apiClient.post('/api/v1/campaigns/c1/sessions', { summaryText: 'hello' })

    const [, init] = fetchSpy.mock.calls[0]
    expect((init as RequestInit).method).toBe('POST')
    expect((init as RequestInit).body).toBe(JSON.stringify({ summaryText: 'hello' }))
    expect((init as RequestInit).headers).toMatchObject({
      'Content-Type': 'application/json',
    })
  })

  it('PATCH serialises the body to JSON', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse({}))

    await apiClient.patch('/api/v1/users/me', { email: 'new@example.com' })

    const [, init] = fetchSpy.mock.calls[0]
    expect((init as RequestInit).method).toBe('PATCH')
    expect((init as RequestInit).body).toBe(JSON.stringify({ email: 'new@example.com' }))
  })

  // ── Envelope parsing ───────────────────────────────────────────────────────

  it('returns the parsed envelope on a successful response', async () => {
    fetchSpy.mockResolvedValueOnce(envelopeResponse({ id: 'campaign-1', name: 'Test' }))

    const result = await apiClient.get<{ id: string; name: string }>('/api/v1/campaigns/c1')

    expect(result.data).toEqual({ id: 'campaign-1', name: 'Test' })
    expect(result.errors).toHaveLength(0)
  })

  it('throws ApiClientError carrying the errors array when the envelope contains errors', async () => {
    fetchSpy.mockResolvedValueOnce(
      errorEnvelopeResponse('UNCERTAIN_ENTITIES_PRESENT', 'Resolve uncertain entities first', 422)
    )

    await expect(apiClient.post('/api/v1/campaigns/c1/sessions/s1/commit')).rejects.toSatisfy(
      (err: unknown) =>
        err instanceof ApiClientError &&
        err.status === 422 &&
        err.errors[0].code === 'UNCERTAIN_ENTITIES_PRESENT'
    )
  })

  // ── Silent refresh — success path ──────────────────────────────────────────

  it('on 401, refreshes the token silently and retries the original request', async () => {
    useAuthStore.setState({ accessToken: 'old-token' })
    fetchSpy
      .mockResolvedValueOnce(unauthorizedResponse()) // original request → 401
      .mockResolvedValueOnce(refreshSuccessResponse('new-token')) // refresh → 200
      .mockResolvedValueOnce(envelopeResponse({ id: '1' })) // retry → 200

    const result = await apiClient.get<{ id: string }>('/api/v1/test')

    expect(result.data).toEqual({ id: '1' })
    // New token must be stored
    expect(useAuthStore.getState().accessToken).toBe('new-token')
    // Retry must carry the new token
    const [, retryInit] = fetchSpy.mock.calls[2]
    expect((retryInit as RequestInit).headers).toMatchObject({
      Authorization: 'Bearer new-token',
    })
  })

  // ── Silent refresh — failure paths ─────────────────────────────────────────

  it('when refresh returns non-200, clears auth state, redirects to /login, and throws', async () => {
    useAuthStore.setState({ accessToken: 'tok-stale' })
    fetchSpy
      .mockResolvedValueOnce(unauthorizedResponse()) // original → 401
      .mockResolvedValueOnce(new Response(null, { status: 400 })) // refresh → failure

    await expect(apiClient.get('/api/v1/test')).rejects.toBeInstanceOf(ApiClientError)
    // logout clears the token
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(mockLocation.href).toBe('/login')
  })

  it('when the retried request also returns 401, clears auth state, redirects, and throws', async () => {
    useAuthStore.setState({ accessToken: 'old-token' })
    fetchSpy
      .mockResolvedValueOnce(unauthorizedResponse()) // original → 401
      .mockResolvedValueOnce(refreshSuccessResponse('new-token')) // refresh → 200
      .mockResolvedValueOnce(unauthorizedResponse()) // retry → 401 again

    await expect(apiClient.get('/api/v1/test')).rejects.toBeInstanceOf(ApiClientError)
    // logout clears the token even though a new token was briefly set during refresh
    expect(useAuthStore.getState().accessToken).toBeNull()
    expect(mockLocation.href).toBe('/login')
  })

  // ── Refresh deduplication ──────────────────────────────────────────────────

  it('deduplicates concurrent 401 responses to a single refresh call', async () => {
    useAuthStore.setState({ accessToken: 'old-token' })
    fetchSpy
      .mockResolvedValueOnce(unauthorizedResponse()) // req A → 401
      .mockResolvedValueOnce(unauthorizedResponse()) // req B → 401
      .mockResolvedValueOnce(refreshSuccessResponse('new-token')) // one refresh call
      .mockResolvedValueOnce(envelopeResponse({ n: 1 })) // req A retry
      .mockResolvedValueOnce(envelopeResponse({ n: 2 })) // req B retry

    const [a, b] = await Promise.all([apiClient.get('/api/v1/test'), apiClient.get('/api/v1/test')])

    expect(a.data).toEqual({ n: 1 })
    expect(b.data).toEqual({ n: 2 })

    const refreshCalls = fetchSpy.mock.calls.filter((args: unknown[]) =>
      (args[0] as string).includes('/auth/refresh')
    )
    expect(refreshCalls).toHaveLength(1)
  })
})
