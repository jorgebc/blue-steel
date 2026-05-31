import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type { AssignableRole, CampaignMemberResponse } from '@/types/member'

/** Query-key factory for campaign-member queries. */
export const memberKeys = {
  all: (campaignId: string) => ['campaigns', campaignId, 'members'] as const,
}

/** Lists the campaign's member roster (member-or-admin on the backend). */
export async function getCampaignMembers(campaignId: string): Promise<CampaignMemberResponse[]> {
  const res = await apiClient.get<CampaignMemberResponse[]>(
    `/api/v1/campaigns/${campaignId}/members`
  )
  return res.data
}

/**
 * Invites a user to the campaign by email (GM only). Creates the account if new,
 * else adds the existing user. The wire `role` is the uppercase enum name the
 * backend deserializes (e.g. `EDITOR`).
 */
export async function inviteCampaignMember(
  campaignId: string,
  body: { email: string; role: AssignableRole }
): Promise<void> {
  await apiClient.post(`/api/v1/campaigns/${campaignId}/invitations`, {
    email: body.email,
    role: body.role.toUpperCase(),
  })
}

/** Changes a member's role (GM only). */
export async function changeMemberRole(
  campaignId: string,
  userId: string,
  role: AssignableRole
): Promise<void> {
  await apiClient.patch(`/api/v1/campaigns/${campaignId}/members/${userId}`, {
    role: role.toUpperCase(),
  })
}

/** Removes a member from the campaign (GM only). */
export async function removeMember(campaignId: string, userId: string): Promise<void> {
  await apiClient.delete(`/api/v1/campaigns/${campaignId}/members/${userId}`)
}

/** Lists the campaign roster. Disabled until a campaign id is available. */
export function useCampaignMembers(campaignId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: memberKeys.all(campaignId ?? ''),
    queryFn: () => getCampaignMembers(campaignId as string),
    enabled: enabled && !!campaignId,
  })
}

/** Invites a member and refreshes the roster cache on success. */
export function useInviteCampaignMember(campaignId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: { email: string; role: AssignableRole }) =>
      inviteCampaignMember(campaignId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: memberKeys.all(campaignId) }),
  })
}

/** Changes a member's role and refreshes the roster cache on success. */
export function useChangeMemberRole(campaignId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (vars: { userId: string; role: AssignableRole }) =>
      changeMemberRole(campaignId, vars.userId, vars.role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: memberKeys.all(campaignId) }),
  })
}

/** Removes a member and refreshes the roster cache on success. */
export function useRemoveMember(campaignId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => removeMember(campaignId, userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: memberKeys.all(campaignId) }),
  })
}
