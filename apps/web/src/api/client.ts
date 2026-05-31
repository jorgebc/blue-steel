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

// Module-level state deduplicates concurrent 401 refresh attempts
let isRefreshing = false
let refreshPromise: Promise<boolean> | null = null

async function attemptTokenRefresh(): Promise<boolean> {
  if (isRefreshing && refreshPromise !== null) return refreshPromise
  isRefreshing = true
  refreshPromise = fetch(`${import.meta.env.VITE_API_BASE_URL}/api/v1/auth/refresh`, {
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
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL}${path}`, {
    method,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
  })

  if (response.status === 401) {
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

export const apiClient = {
  get: <T>(path: string): Promise<ApiEnvelope<T>> => request<T>('GET', path),
  post: <T>(path: string, body?: unknown): Promise<ApiEnvelope<T>> =>
    request<T>('POST', path, body),
  patch: <T>(path: string, body?: unknown): Promise<ApiEnvelope<T>> =>
    request<T>('PATCH', path, body),
  delete: <T>(path: string): Promise<ApiEnvelope<T>> => request<T>('DELETE', path),
}
