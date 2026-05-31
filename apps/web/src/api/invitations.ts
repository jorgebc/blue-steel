import { useMutation } from '@tanstack/react-query'
import { apiClient } from './client'

/**
 * Invites a user to the platform by email (admin only). The backend creates the
 * account (201) or refreshes credentials for an existing one (200); either way
 * the caller only needs success vs. failure, so the response body is ignored.
 */
export async function invitePlatformUser(email: string): Promise<void> {
  await apiClient.post('/api/v1/invitations', { email })
}

/** Mutation hook for the admin platform-invite surface. */
export function useInvitePlatformUser() {
  return useMutation({ mutationFn: invitePlatformUser })
}
