import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient, ApiClientError } from './client'
import type {
  DiffPayload,
  SessionAcceptedResponse,
  SessionStatusResponse,
  SubmitSessionRequest,
} from '@/types/session'

/** Query-key factory for session queries, scoped per campaign. */
export const sessionKeys = {
  all: (campaignId: string) => ['sessions', campaignId] as const,
  status: (campaignId: string, sessionId: string) =>
    [...sessionKeys.all(campaignId), sessionId, 'status'] as const,
  diff: (campaignId: string, sessionId: string) =>
    [...sessionKeys.all(campaignId), sessionId, 'diff'] as const,
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

/** Fetches the reviewed draft diff. Valid only while the session is in DRAFT (404 otherwise). */
export async function getSessionDiff(campaignId: string, sessionId: string): Promise<DiffPayload> {
  const res = await apiClient.get<DiffPayload>(
    `/api/v1/campaigns/${campaignId}/sessions/${sessionId}/diff`
  )
  return res.data
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
