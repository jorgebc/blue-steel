import { useQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import { useCampaignStore } from '@/store/campaignStore'
import type {
  EntityDetail,
  EntityLinks,
  EntityListPage,
  EntitySummary,
  EntityType,
} from '@/types/worldstate'

const PAGE_SIZE = 20

/** Query-key factory for world-state reads, scoped per campaign and entity type. */
export const worldstateKeys = {
  all: (campaignId: string) => ['worldstate', campaignId] as const,
  list: (campaignId: string, entityType: EntityType, page: number, search = '') =>
    [...worldstateKeys.all(campaignId), entityType, 'list', page, search] as const,
  detail: (campaignId: string, entityType: EntityType, entityId: string) =>
    [...worldstateKeys.all(campaignId), entityType, 'detail', entityId] as const,
}

/** Query-key factory for an entity's profile cross-links (F4.7), nested under its detail key. */
export const entityLinksKeys = {
  links: (campaignId: string, entityType: EntityType, entityId: string) =>
    [...worldstateKeys.detail(campaignId, entityType, entityId), 'links'] as const,
}

/**
 * Fetches one offset-paginated page of entity summaries. Unlike the session fetchers, this reads
 * the envelope `meta` ({ page, size, totalCount }) so callers can drive prev/next paging (D-055).
 */
export async function getEntities(
  campaignId: string,
  entityType: EntityType,
  page: number,
  size: number = PAGE_SIZE,
  search = ''
): Promise<EntityListPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (search) params.set('q', search)
  const res = await apiClient.get<EntitySummary[]>(
    `/api/v1/campaigns/${campaignId}/${entityType}s?${params.toString()}`
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

/**
 * Offset-paginated entity list for the active campaign. Page is zero-based. When `search` is
 * non-empty the backend restricts results to names containing it (case-insensitive).
 */
export function useEntityList(entityType: EntityType, page: number, search = '') {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  return useQuery({
    queryKey: worldstateKeys.list(campaignId ?? '', entityType, page, search),
    queryFn: () => getEntities(campaignId ?? '', entityType, page, PAGE_SIZE, search),
    enabled: campaignId !== null,
  })
}

/**
 * Fetches all entity summaries for the active campaign in a single request (size=100, matching
 * backend MAX_PAGE_SIZE). Use this for graph views that need every node — not for paginated lists.
 * (FU3)
 */
export function useAllEntities(entityType: EntityType) {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  return useQuery({
    queryKey: [...worldstateKeys.all(campaignId ?? ''), entityType, 'all-items'] as const,
    queryFn: async () => {
      const page = await getEntities(campaignId ?? '', entityType, 0, 100)
      return page.items
    },
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

/** Fetches an entity's profile cross-links. Only actor and space expose a `/links` endpoint (F4.7). */
export async function getEntityLinks(
  campaignId: string,
  entityType: EntityType,
  entityId: string
): Promise<EntityLinks> {
  const res = await apiClient.get<EntityLinks>(
    `/api/v1/campaigns/${campaignId}/${entityType}s/${entityId}/links`
  )
  return res.data
}

/** Cross-link bundle (relations, related entities, events, sessions) for the active campaign. */
export function useEntityLinks(entityType: EntityType, entityId: string) {
  const campaignId = useCampaignStore((s) => s.activeCampaignId)
  return useQuery({
    queryKey: entityLinksKeys.links(campaignId ?? '', entityType, entityId),
    queryFn: () => getEntityLinks(campaignId ?? '', entityType, entityId),
    enabled: campaignId !== null && entityId !== '',
  })
}
