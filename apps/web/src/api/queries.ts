import { useMutation, useQuery } from '@tanstack/react-query'
import { apiClient } from './client'
import type { QueryRequest, QueryResponse, QueryUsage } from '@/types/query'

/**
 * Posts a natural-language question and waits synchronously for the grounded answer (D-052).
 * A backend timeout surfaces as `ApiClientError` with `status === 504` (`QUERY_TIMEOUT`); the
 * page branches on that to suggest rephrasing.
 */
export async function submitQuery(campaignId: string, question: string): Promise<QueryResponse> {
  const res = await apiClient.post<QueryResponse>(`/api/v1/campaigns/${campaignId}/queries`, {
    question,
  } satisfies QueryRequest)
  return res.data
}

/**
 * Mutation for submitting a query. Stateless by design — no query key, no cache, no
 * invalidation (Query Mode keeps no Q&A history in v1, D-058).
 */
export function useSubmitQuery(campaignId: string) {
  return useMutation({
    mutationFn: (question: string) => submitQuery(campaignId, question),
  })
}

/** Key for the shared Query Mode usage figure; invalidate after a submission to refresh it. */
export const queryUsageKey = (campaignId: string) => ['query-usage', campaignId] as const

/** Reads the shared daily LLM budget and the caller's remaining rate-limit headroom (D-096). */
export async function fetchQueryUsage(campaignId: string): Promise<QueryUsage> {
  const res = await apiClient.get<QueryUsage>(`/api/v1/campaigns/${campaignId}/queries/usage`)
  return res.data
}

/**
 * Usage is genuine server state (unlike the stateless query mutation, D-058), so it lives in the
 * TanStack Query cache. Enabled only once a campaign is selected.
 */
export function useQueryUsage(campaignId: string) {
  return useQuery({
    queryKey: queryUsageKey(campaignId),
    queryFn: () => fetchQueryUsage(campaignId),
    enabled: campaignId !== '',
  })
}
