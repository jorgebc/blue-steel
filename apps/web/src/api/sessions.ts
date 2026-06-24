import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, ApiClientError } from './client'
import type {
  CommitPayload,
  DiffPayload,
  SessionAcceptedResponse,
  SessionDetail,
  SessionStatusResponse,
  SessionSummary,
  SubmitSessionRequest,
} from '@/types/session'

/** Query-key factory for session queries, scoped per campaign. */
export const sessionKeys = {
  all: (campaignId: string) => ['sessions', campaignId] as const,
  lists: (campaignId: string) => [...sessionKeys.all(campaignId), 'list'] as const,
  list: (campaignId: string, page: number) => [...sessionKeys.lists(campaignId), page] as const,
  status: (campaignId: string, sessionId: string) =>
    [...sessionKeys.all(campaignId), sessionId, 'status'] as const,
  diff: (campaignId: string, sessionId: string) =>
    [...sessionKeys.all(campaignId), sessionId, 'diff'] as const,
  detail: (campaignId: string, sessionId: string) =>
    [...sessionKeys.all(campaignId), sessionId, 'detail'] as const,
}

/** Submits a raw session summary, kicking off the async extraction pipeline. */
export async function submitSession(
  campaignId: string,
  body: SubmitSessionRequest
): Promise<SessionAcceptedResponse> {
  const res = await apiClient.post<SessionAcceptedResponse>(
    `/api/v1/campaigns/${campaignId}/sessions`,
    body
  )
  return res.data
}

/** Fetches the current pipeline status for a session. */
export async function getSessionStatus(
  campaignId: string,
  sessionId: string
): Promise<SessionStatusResponse> {
  const res = await apiClient.get<SessionStatusResponse>(
    `/api/v1/campaigns/${campaignId}/sessions/${sessionId}/status`
  )
  return res.data
}

/** Fetches a single session's read-only detail (any campaign member; F4.8.3). */
export async function getSessionDetail(
  campaignId: string,
  sessionId: string
): Promise<SessionDetail> {
  const res = await apiClient.get<SessionDetail>(
    `/api/v1/campaigns/${campaignId}/sessions/${sessionId}`
  )
  return res.data
}

/** Fetches the reviewed draft diff. Valid only while the session is in DRAFT (404 otherwise). */
export async function getSessionDiff(campaignId: string, sessionId: string): Promise<DiffPayload> {
  const res = await apiClient.get<DiffPayload>(
    `/api/v1/campaigns/${campaignId}/sessions/${sessionId}/diff`
  )
  return res.data
}

/** Commits a reviewed draft to world state (200; response body is empty). */
export async function commitSession(
  campaignId: string,
  sessionId: string,
  payload: CommitPayload
): Promise<void> {
  await apiClient.post<void>(
    `/api/v1/campaigns/${campaignId}/sessions/${sessionId}/commit`,
    payload
  )
}

/** Discards a draft session (GM only; 200; response body is empty). */
export async function discardSession(campaignId: string, sessionId: string): Promise<void> {
  await apiClient.delete<void>(`/api/v1/campaigns/${campaignId}/sessions/${sessionId}`)
}

/** Submits a session and refreshes the campaign's session cache on success. */
export function useSubmitSession(campaignId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: SubmitSessionRequest) => submitSession(campaignId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: sessionKeys.all(campaignId) }),
  })
}

/**
 * Polls a session's status, refetching every 2s only while `PROCESSING`; any
 * terminal state stops the polling. Disabled until `enabled` is true.
 */
export function useSessionStatus(campaignId: string, sessionId: string, enabled: boolean) {
  return useQuery({
    queryKey: sessionKeys.status(campaignId, sessionId),
    queryFn: () => getSessionStatus(campaignId, sessionId),
    enabled,
    refetchInterval: (query) => (query.state.data?.status === 'PROCESSING' ? 2000 : false),
  })
}

/** Fetches a session's read-only detail once. Disabled until both ids are present. */
export function useSessionDetail(campaignId: string, sessionId: string) {
  return useQuery({
    queryKey: sessionKeys.detail(campaignId, sessionId),
    queryFn: () => getSessionDetail(campaignId, sessionId),
    enabled: campaignId !== '' && sessionId !== '',
  })
}

/**
 * Fetches the draft diff once (no polling — the diff is fixed while in DRAFT).
 * Disabled until `enabled` is true.
 */
export function useSessionDiff(campaignId: string, sessionId: string, enabled = true) {
  return useQuery({
    queryKey: sessionKeys.diff(campaignId, sessionId),
    queryFn: () => getSessionDiff(campaignId, sessionId),
    enabled,
  })
}

/** Commits a draft and refreshes the campaign's session list on success. */
export function useCommitSession(campaignId: string, sessionId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (payload: CommitPayload) => commitSession(campaignId, sessionId, payload),
    // Invalidate only the list, not the whole `sessions` prefix — the latter also
    // matches this session's still-mounted `diff` query and would refetch a now
    // COMMITTED session, yielding a spurious 404 before the page navigates away.
    onSuccess: () => queryClient.invalidateQueries({ queryKey: sessionKeys.lists(campaignId) }),
  })
}

/** Discards a draft and refreshes the campaign's session list on success. */
export function useDiscardSession(campaignId: string, sessionId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => discardSession(campaignId, sessionId),
    // Same reasoning as commit: avoid refetching the discarded session's diff (404).
    onSuccess: () => queryClient.invalidateQueries({ queryKey: sessionKeys.lists(campaignId) }),
  })
}

/** Fetches a page of session summaries for a campaign (offset pagination, D-055, page size 20). */
export async function getSessions(campaignId: string, page: number): Promise<SessionSummary[]> {
  const res = await apiClient.get<SessionSummary[]>(
    `/api/v1/campaigns/${campaignId}/sessions?page=${page}&size=20`
  )
  return res.data
}

/** Offset-paginated session list. Page is zero-based. */
export function useSessions(campaignId: string, page: number) {
  return useQuery({
    queryKey: sessionKeys.list(campaignId, page),
    queryFn: () => getSessions(campaignId, page),
  })
}

const UUID_PATTERN = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/i

/**
 * Extracts the existing draft's session id from an `ACTIVE_SESSION_EXISTS` error.
 * The envelope carries no structured field for it, so the UUID is parsed from the
 * error message text. Returns null for any other error or when no UUID is present.
 */
export function extractExistingSessionId(error: unknown): string | null {
  if (!(error instanceof ApiClientError)) return null
  const conflict = error.errors.find((e) => e.code === 'ACTIVE_SESSION_EXISTS')
  if (!conflict) return null
  return conflict.message.match(UUID_PATTERN)?.[0] ?? null
}
