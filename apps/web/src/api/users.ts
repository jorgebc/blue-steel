import { useMutation } from '@tanstack/react-query'
import { apiClient } from './client'

type ChangePasswordPayload = { currentPassword: string; newPassword: string }

export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  await apiClient.patch('/api/v1/users/me/password', payload)
}

export function useChangePassword() {
  return useMutation({ mutationFn: changePassword })
}
