import { useMutation } from '@tanstack/react-query'
import { apiClient } from './client'
import type { QueryRequest, QueryResponse } from '@/types/query'

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
