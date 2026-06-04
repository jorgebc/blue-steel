import { useQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import { useCampaignStore } from '@/store/campaignStore'
import type { Relation, RelationDetail } from '@/types/relation'

/** Query-key factory for relation reads, scoped per campaign. */
export const relationKeys = {
  all: (campaignId: string) => ['relations', campaignId] as const,
  list: (campaignId: string) => [...relationKeys.all(campaignId), 'list'] as const,
  detail: (campaignId: string, relationId: string) =>
    [...relationKeys.all(campaignId), 'detail', relationId] as const,
}

/** Fetches every relation in the campaign with its structured graph endpoints (F4.3.6). */
export async function getRelations(campaignId: string): Promise<Relation[]> {
  const res = await apiClient.get<Relation[]>(`/api/v1/campaigns/${campaignId}/relations`)
  return res.data
}

/** Fetches a single relation with its endpoints and full version history. */
export async function getRelationDetail(
  campaignId: string,
  relationId: string
): Promise<RelationDetail> {
  const res = await apiClient.get<RelationDetail>(
    `/api/v1/campaigns/${campaignId}/relations/${relationId}`
  )
  return res.data
}

/** All relations for the active campaign. */
export function useRelations() {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  return useQuery({
    queryKey: relationKeys.list(campaignId ?? ''),
    queryFn: () => getRelations(campaignId ?? ''),
    enabled: campaignId !== null,
  })
}

/** Single relation detail (endpoints + version history) for the active campaign. */
export function useRelationDetail(relationId: string) {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  return useQuery({
    queryKey: relationKeys.detail(campaignId ?? '', relationId),
    queryFn: () => getRelationDetail(campaignId ?? '', relationId),
    enabled: campaignId !== null && relationId !== '',
  })
}
