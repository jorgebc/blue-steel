import { useQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import { useCampaignStore } from '@/store/campaignStore'
import type { EntityDetail, EntityListPage, EntitySummary, EntityType } from '@/types/worldstate'

const PAGE_SIZE = 20

/** Query-key factory for world-state reads, scoped per campaign and entity type. */
export const worldstateKeys = {
  all: (campaignId: string) => ['worldstate', campaignId] as const,
  list: (campaignId: string, entityType: EntityType, page: number) =>
    [...worldstateKeys.all(campaignId), entityType, 'list', page] as const,
  detail: (campaignId: string, entityType: EntityType, entityId: string) =>
    [...worldstateKeys.all(campaignId), entityType, 'detail', entityId] as const,
}

/**
 * Fetches one offset-paginated page of entity summaries. Unlike the session fetchers, this reads
 * the envelope `meta` ({ page, size, totalCount }) so callers can drive prev/next paging (D-055).
 */
export async function getEntities(
  campaignId: string,
  entityType: EntityType,
  page: number
): Promise<EntityListPage> {
  const res = await apiClient.get<EntitySummary[]>(
    `/api/v1/campaigns/${campaignId}/${entityType}s?page=${page}&size=${PAGE_SIZE}`
  )
  const meta = (res.meta ?? {}) as { page?: number; size?: number; totalCount?: number }
  return {
    items: res.data,
    page: meta.page ?? page,
    size: meta.size ?? PAGE_SIZE,
    totalCount: meta.totalCount ?? res.data.length,
  }
}

/** Fetches a single entity with its full version history. */
export async function getEntityDetail(
  campaignId: string,
  entityType: EntityType,
  entityId: string
): Promise<EntityDetail> {
  const res = await apiClient.get<EntityDetail>(
    `/api/v1/campaigns/${campaignId}/${entityType}s/${entityId}`
  )
  return res.data
}

/** Offset-paginated entity list for the active campaign. Page is zero-based. */
export function useEntityList(entityType: EntityType, page: number) {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  return useQuery({
    queryKey: worldstateKeys.list(campaignId ?? '', entityType, page),
    queryFn: () => getEntities(campaignId ?? '', entityType, page),
    enabled: campaignId !== null,
  })
}

/** Single entity detail (snapshot + version history) for the active campaign. */
export function useEntityDetail(entityType: EntityType, entityId: string) {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  return useQuery({
    queryKey: worldstateKeys.detail(campaignId ?? '', entityType, entityId),
    queryFn: () => getEntityDetail(campaignId ?? '', entityType, entityId),
    enabled: campaignId !== null && entityId !== '',
  })
}
