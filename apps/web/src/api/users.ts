import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import type { UserSearchResult } from '@/types/auth'

type ChangePasswordPayload = { currentPassword: string; newPassword: string }

export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  await apiClient.patch('/api/v1/users/me/password', payload)
}

export function useChangePassword() {
  return useMutation({ mutationFn: changePassword })
}

/** Searches users by email (admin or GM-anywhere) for the GM picker. */
export async function searchUsers(email: string): Promise<UserSearchResult[]> {
  const res = await apiClient.get<UserSearchResult[]>(
    `/api/v1/users?email=${encodeURIComponent(email)}`
  )
  return res.data
}

/** Shortest fragment that triggers a search — mirrors the backend minimum. */
export const USER_SEARCH_MIN_LENGTH = 2

/**
 * Looks up users whose email contains {@code email}. Disabled until the fragment
 * reaches {@link USER_SEARCH_MIN_LENGTH} (or when {@code enabled} is false) so it
 * never fires an overly broad search.
 */
export function useUserSearch(email: string, enabled = true) {
  return useQuery({
    queryKey: ['users', 'search', email],
    queryFn: () => searchUsers(email),
    enabled: enabled && email.length >= USER_SEARCH_MIN_LENGTH,
  })
}
