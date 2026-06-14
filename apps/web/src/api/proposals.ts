import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type {
  CreateProposalRequest,
  DecideProposalRequest,
  Proposal,
  ProposalDecisionResult,
  ProposalListPage,
  ProposalStatus,
  ProposalTargetType,
} from '@/types/proposal'

const PAGE_SIZE = 20

/** Query-key factory for proposal reads, scoped per campaign. */
export const proposalKeys = {
  all: (campaignId: string) => ['proposals', campaignId] as const,
  list: (campaignId: string, status: ProposalStatus | undefined, page: number) =>
    [...proposalKeys.all(campaignId), 'list', { status: status ?? null, page }] as const,
  target: (campaignId: string, targetType: ProposalTargetType, targetId: string) =>
    [...proposalKeys.all(campaignId), 'target', { targetType, targetId }] as const,
}

/**
 * Fetches one offset-paginated page of proposals, optionally filtered by status. Reads the envelope
 * `meta` ({ page, size, totalCount }) so callers can drive paging.
 */
export async function getProposals(
  campaignId: string,
  status?: ProposalStatus,
  page = 0,
  size: number = PAGE_SIZE
): Promise<ProposalListPage> {
  const params = new URLSearchParams()
  if (status) params.set('status', status)
  params.set('page', String(page))
  params.set('size', String(size))
  return fetchProposals(campaignId, params, page)
}

/**
 * Fetches every proposal for one entity (the profile thread), server-side filtered by target so no
 * proposal is missed beyond the first page. Returned unpaginated by the backend.
 */
export async function getProposalsForTarget(
  campaignId: string,
  targetType: ProposalTargetType,
  targetId: string
): Promise<ProposalListPage> {
  const params = new URLSearchParams()
  params.set('targetType', targetType)
  params.set('targetId', targetId)
  return fetchProposals(campaignId, params, 0)
}

async function fetchProposals(
  campaignId: string,
  params: URLSearchParams,
  page: number
): Promise<ProposalListPage> {
  const res = await apiClient.get<Proposal[]>(
    `/api/v1/campaigns/${campaignId}/proposals?${params.toString()}`
  )
  const meta = (res.meta ?? {}) as { page?: number; size?: number; totalCount?: number }
  return {
    proposals: res.data,
    page: meta.page ?? page,
    size: meta.size ?? PAGE_SIZE,
    totalCount: meta.totalCount ?? res.data.length,
  }
}

/** Submits a new proposal; returns the persisted proposal (status OPEN). */
export async function createProposal(
  campaignId: string,
  body: CreateProposalRequest
): Promise<Proposal> {
  const res = await apiClient.post<Proposal>(`/api/v1/campaigns/${campaignId}/proposals`, body)
  return res.data
}

/** Casts a co-sign vote (no request body); returns the updated proposal (status COSIGNED). */
export async function coSignProposal(campaignId: string, proposalId: string): Promise<Proposal> {
  const res = await apiClient.post<Proposal>(
    `/api/v1/campaigns/${campaignId}/proposals/${proposalId}/votes`
  )
  return res.data
}

/** Records the GM's approve-with-edit or veto decision; returns the resulting version id (null on veto). */
export async function decideProposal(
  campaignId: string,
  proposalId: string,
  body: DecideProposalRequest
): Promise<ProposalDecisionResult> {
  const res = await apiClient.post<ProposalDecisionResult>(
    `/api/v1/campaigns/${campaignId}/proposals/${proposalId}/decision`,
    body
  )
  return res.data
}

/** Offset-paginated proposal list for a campaign, optionally filtered by status. Page is zero-based. */
export function useProposals(campaignId: string, status?: ProposalStatus, page = 0) {
  return useQuery({
    queryKey: proposalKeys.list(campaignId, status, page),
    queryFn: () => getProposals(campaignId, status, page),
    enabled: campaignId !== '',
  })
}

/** Every proposal for a single entity, newest first (the profile thread, F5.8). */
export function useProposalsForTarget(
  campaignId: string,
  targetType: ProposalTargetType,
  targetId: string
) {
  return useQuery({
    queryKey: proposalKeys.target(campaignId, targetType, targetId),
    queryFn: () => getProposalsForTarget(campaignId, targetType, targetId),
    enabled: campaignId !== '',
  })
}

/** The GM review queue: proposals that have been co-signed and await a decision (F5.9). */
export function useCosignedProposals(campaignId: string) {
  return useProposals(campaignId, 'COSIGNED')
}

/** Submits a proposal and refreshes the campaign's proposal cache on success. */
export function useCreateProposal(campaignId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateProposalRequest) => createProposal(campaignId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: proposalKeys.all(campaignId) }),
  })
}

/** Co-signs a proposal and refreshes the campaign's proposal cache on success. */
export function useCoSignProposal(campaignId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (proposalId: string) => coSignProposal(campaignId, proposalId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: proposalKeys.all(campaignId) }),
  })
}

/** Records a GM decision and refreshes the campaign's proposal cache on success. */
export function useDecideProposal(campaignId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (vars: { proposalId: string; body: DecideProposalRequest }) =>
      decideProposal(campaignId, vars.proposalId, vars.body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: proposalKeys.all(campaignId) }),
  })
}
