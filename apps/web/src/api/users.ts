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

/**
 * Looks up users matching {@code email}. Disabled while the query is empty (or
 * when {@code enabled} is false) so it never fires a wildcard search.
 */
export function useUserSearch(email: string, enabled = true) {
  return useQuery({
    queryKey: ['users', 'search', email],
    queryFn: () => searchUsers(email),
    enabled: enabled && email.length > 0,
  })
}
