import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiBaseUrl, apiClient } from './client'
import { useAuthStore } from '@/store/authStore'
import { useSettingsStore } from '@/store/settingsStore'
import type { ApiEnvelope } from '@/types/api'
import type { AuthLoginResponse, RefreshResponse, UserMeResponse } from '@/types/auth'

// For cache invalidation by downstream tasks (e.g. F1.7.5+)
export const authKeys = {
  currentUser: () => ['users', 'me'] as const,
}

// ─── App-boot session restore ────────────────────────────────────────────────

/**
 * Attempts a silent token refresh on app startup using the httpOnly cookie.
 * On success, restores accessToken + currentUser in the auth store so a hard
 * refresh does not log the user out. Always resolves — failures are silent.
 */
export async function initAuth(): Promise<void> {
  try {
    const res = await fetch(`${apiBaseUrl()}/api/v1/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
    })
    if (!res.ok) return
    const envelope = (await res.json()) as ApiEnvelope<RefreshResponse>
    if (envelope.errors?.length) return
    useAuthStore.getState().setAccessToken(envelope.data.accessToken)
    const me = await getCurrentUser()
    useAuthStore.getState().setCurrentUser(me)
  } catch {
    // No valid cookie or network error — user must log in
  }
}

// ─── Raw fetch functions ────────────────────────────────────────────────────

export async function login(credentials: {
  email: string
  password: string
}): Promise<AuthLoginResponse> {
  const res = await apiClient.post<AuthLoginResponse>('/api/v1/auth/login', credentials)
  return res.data
}

export async function logout(): Promise<void> {
  await apiClient.post('/api/v1/auth/logout')
}

export async function getCurrentUser(): Promise<UserMeResponse> {
  const res = await apiClient.get<UserMeResponse>('/api/v1/users/me')
  return res.data
}

// ─── Hooks ──────────────────────────────────────────────────────────────────

export function useLogin() {
  return useMutation({
    mutationFn: async (credentials: { email: string; password: string }) => {
      const loginData = await login(credentials)
      useAuthStore.getState().setAccessToken(loginData.accessToken)
      const me = await getCurrentUser()
      useAuthStore.getState().setCurrentUser(me)
      useSettingsStore.getState().hydrateFromUser(me)
      return loginData
    },
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onSettled: () => {
      useAuthStore.getState().logout()
      queryClient.clear()
    },
  })
}
