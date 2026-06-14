/**
 * Hand-written mirrors of the backend proposal DTOs (Phase 5, D-076). The status, target-type, and
 * decision enums are serialized by Jackson as the Java enum *name*, so they are UPPERCASE on the wire
 * — these unions match that exactly (no client-side normalization).
 */

/** Entity category a proposal targets. v2 supports actor and space only (D-108). UPPERCASE on the wire. */
export type ProposalTargetType = 'ACTOR' | 'SPACE'

/** Proposal lifecycle state (D-105/D-109). UPPERCASE on the wire. */
export type ProposalStatus = 'OPEN' | 'COSIGNED' | 'APPROVED' | 'REJECTED' | 'EXPIRED'

/** GM decision verb (D-110). UPPERCASE on the wire. */
export type ProposalDecision = 'APPROVE' | 'REJECT'

/**
 * One proposal. `proposedDelta` is a flat `{ field: newValue }` map mirroring an entity version's
 * changed fields (D-104). `sessionId` is creator-selected provenance; `resultingEntityVersionId` is
 * the back-reference written on approval, null until then (D-107).
 */
export interface Proposal {
  proposalId: string
  campaignId: string
  targetType: ProposalTargetType
  targetId: string
  ownerId: string
  status: ProposalStatus
  proposedDelta: Record<string, unknown>
  sessionId: string
  resultingEntityVersionId: string | null
  expiresAt: string
  createdAt: string
}

/** Request body for submitting a proposal. */
export interface CreateProposalRequest {
  targetType: ProposalTargetType
  targetId: string
  sessionId: string
  proposedDelta: Record<string, unknown>
}

/**
 * Request body for a GM decision. `editedDelta` (D-110) is sent only on APPROVE when the GM changed
 * the author's delta; omit/null otherwise. Must never accompany REJECT.
 */
export interface DecideProposalRequest {
  decision: ProposalDecision
  editedDelta?: Record<string, unknown> | null
}

/** Result of a GM decision: the new entity version id on approval, null on veto (D-107). */
export interface ProposalDecisionResult {
  resultingEntityVersionId: string | null
}

/** One offset-paginated page of proposals plus the envelope `meta` paging fields. */
export interface ProposalListPage {
  proposals: Proposal[]
  page: number
  size: number
  totalCount: number
}
