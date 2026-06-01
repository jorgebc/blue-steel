import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type { CampaignResponse } from '@/types/campaign'

/** Query-key factory for campaign queries. */
export const campaignKeys = {
  all: ['campaigns'] as const,
  detail: (id: string) => [...campaignKeys.all, id] as const,
}

/** Lists the campaigns the caller belongs to (admins receive all). */
export async function getCampaigns(): Promise<CampaignResponse[]> {
  const res = await apiClient.get<CampaignResponse[]>('/api/v1/campaigns')
  return res.data
}

/** Fetches a single campaign by id, including the caller's resolved role. */
export async function getCampaign(id: string): Promise<CampaignResponse> {
  const res = await apiClient.get<CampaignResponse>(`/api/v1/campaigns/${id}`)
  return res.data
}

/** Lists the authenticated user's campaigns. */
export function useCampaigns() {
  return useQuery({
    queryKey: campaignKeys.all,
    queryFn: getCampaigns,
  })
}

/**
 * Fetches a single campaign by id. Disabled until an id is available so the
 * query never fires with an empty path.
 */
export function useCampaign(id: string | undefined) {
  return useQuery({
    queryKey: campaignKeys.detail(id ?? ''),
    queryFn: () => getCampaign(id as string),
    enabled: !!id,
  })
}

/** Creates a campaign with its GM (admin-only on the backend). */
export async function createCampaign(body: {
  name: string
  gmUserId: string
}): Promise<CampaignResponse> {
  const res = await apiClient.post<CampaignResponse>('/api/v1/campaigns', body)
  return res.data
}

/** Creates a campaign and refreshes the campaign list cache on success. */
export function useCreateCampaign() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createCampaign,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: campaignKeys.all }),
  })
}

/** Permanently deletes a campaign and all its data (admin-only on the backend). */
export async function deleteCampaign(id: string): Promise<void> {
  await apiClient.delete(`/api/v1/campaigns/${id}`)
}

/** Deletes a campaign and purges the full campaigns cache subtree on success. */
export function useDeleteCampaign() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteCampaign,
    onSuccess: () =>
      queryClient.removeQueries({ queryKey: campaignKeys.all }),
  })
}
