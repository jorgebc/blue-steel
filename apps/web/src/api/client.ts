import type { ApiEnvelope, ApiError } from '@/types/api'
import type { RefreshResponse } from '@/types/auth'
import { useAuthStore } from '@/store/authStore'

export class ApiClientError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly errors: ApiError[]
  ) {
    super(message)
    this.name = 'ApiClientError'
  }
}

let envWarningEmitted = false

export function apiBaseUrl(): string {
  const url = import.meta.env.VITE_API_BASE_URL
  if (!url) {
    // Without this, fetch would target "undefined/api/..." and fail opaquely.
    // Warn once per page load — every fetch hitting this is the same root cause.
    if (!envWarningEmitted) {
      envWarningEmitted = true
      console.error(
        'VITE_API_BASE_URL is not set. Copy apps/web/.env.example to apps/web/.env.local and restart `npm run dev`.'
      )
    }
    return ''
  }
  return url
}

// Module-level state deduplicates concurrent 401 refresh attempts
let isRefreshing = false
let refreshPromise: Promise<boolean> | null = null

async function attemptTokenRefresh(): Promise<boolean> {
  if (isRefreshing && refreshPromise !== null) return refreshPromise
  isRefreshing = true
  refreshPromise = fetch(`${apiBaseUrl()}/api/v1/auth/refresh`, {
    method: 'POST',
    credentials: 'include',
  })
    .then(async (res) => {
      if (!res.ok) return false
      const envelope = (await res.json()) as ApiEnvelope<RefreshResponse>
      if (envelope.errors?.length) return false
      useAuthStore.getState().setAccessToken(envelope.data.accessToken)
      return true
    })
    .catch(() => false)
    .finally(() => {
      isRefreshing = false
      refreshPromise = null
    })
  return refreshPromise
}

// retried=true prevents a second 401 from triggering another refresh cycle
async function request<T>(
  method: string,
  path: string,
  body?: unknown,
  retried = false
): Promise<ApiEnvelope<T>> {
  const token = useAuthStore.getState().accessToken
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    method,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  })

  if (response.status === 401) {
    // A 401 on the login endpoint means bad credentials, not an expired
    // session — skip the refresh+redirect cycle and surface the error body.
    if (path === '/api/v1/auth/login') {
      const envelope = (await response.json()) as ApiEnvelope<T>
      throw new ApiClientError(
        envelope.errors?.[0]?.message ?? 'Invalid email or password.',
        401,
        envelope.errors ?? []
      )
    }
    if (!retried) {
      const refreshed = await attemptTokenRefresh()
      if (refreshed) return request<T>(method, path, body, true)
    }
    useAuthStore.getState().logout()
    window.location.href = '/login'
    throw new ApiClientError('Session expired', 401, [])
  }

  const envelope = (await response.json()) as ApiEnvelope<T>
  if (envelope.errors?.length) {
    throw new ApiClientError(envelope.errors[0].message, response.status, envelope.errors)
  }
  return envelope
}

const DEFAULT_DOWNLOAD_FILENAME = 'download'

// Parse the filename from a Content-Disposition header, falling back to a
// default. Handles the quoted form the backend emits (filename="…").
function parseContentDispositionFilename(header: string | null): string {
  if (!header) return DEFAULT_DOWNLOAD_FILENAME
  const match = /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header)
  return match ? decodeURIComponent(match[1].trim()) : DEFAULT_DOWNLOAD_FILENAME
}

// Like request(), but for binary/file responses: reads the body as a Blob and
// extracts the download filename instead of parsing a JSON envelope. Shares the
// same auth header + 401 silent-refresh+retry machinery.
async function downloadRequest(
  path: string,
  retried = false
): Promise<{ blob: Blob; filename: string }> {
  const token = useAuthStore.getState().accessToken
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    method: 'GET',
    credentials: 'include',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })

  if (response.status === 401) {
    if (!retried) {
      const refreshed = await attemptTokenRefresh()
      if (refreshed) return downloadRequest(path, true)
    }
    useAuthStore.getState().logout()
    window.location.href = '/login'
    throw new ApiClientError('Session expired', 401, [])
  }

  if (!response.ok) {
    // A failed download (e.g. 422 size cap) still emits the error envelope.
    const envelope = (await response.json()) as ApiEnvelope<null>
    throw new ApiClientError(
      envelope.errors?.[0]?.message ?? 'Download failed.',
      response.status,
      envelope.errors ?? []
    )
  }

  const blob = await response.blob()
  const filename = parseContentDispositionFilename(response.headers.get('Content-Disposition'))
  return { blob, filename }
}

export const apiClient = {
  get: <T>(path: string): Promise<ApiEnvelope<T>> => request<T>('GET', path),
  post: <T>(path: string, body?: unknown): Promise<ApiEnvelope<T>> =>
    request<T>('POST', path, body),
  patch: <T>(path: string, body?: unknown): Promise<ApiEnvelope<T>> =>
    request<T>('PATCH', path, body),
  delete: <T>(path: string): Promise<ApiEnvelope<T>> => request<T>('DELETE', path),
  download: (path: string): Promise<{ blob: Blob; filename: string }> => downloadRequest(path),
}
