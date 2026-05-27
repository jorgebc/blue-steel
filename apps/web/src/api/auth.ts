import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import { useAuthStore } from '@/store/authStore'
import type { AuthLoginResponse, UserMeResponse } from '@/types/auth'

// For cache invalidation by downstream tasks (e.g. F1.7.5+)
export const authKeys = {
  currentUser: () => ['users', 'me'] as const,
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
