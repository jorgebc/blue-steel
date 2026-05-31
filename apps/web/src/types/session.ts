/**
 * Session status as serialized by the backend `SessionStatus` Java enum (Jackson
 * emits the enum *name*, so these are UPPERCASE on the wire).
 */
export type SessionStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'DRAFT'
  | 'COMMITTED'
  | 'FAILED'
  | 'DISCARDED'

export interface SubmitSessionRequest {
  summaryText: string
}

export interface SessionAcceptedResponse {
  sessionId: string
  status: SessionStatus
}

/**
 * Status payload polled while the extraction pipeline runs. `failureReason` is a
 * free-form code that grows across phases (e.g. `PIPELINE_NOT_IMPLEMENTED`), so it
 * is typed as a plain string rather than a closed union.
 */
export interface SessionStatusResponse {
  sessionId: string
  status: SessionStatus
  failureReason: string | null
  message: string | null
}

// ─── Diff review (ARCHITECTURE §7.6) ─────────────────────────────────────────
// Read-only payload returned by GET .../diff when a session is in DRAFT. Mirrors
// the F2.7 Java records exactly. Never POSTed back (the commit payload is a
// separate derived shape — F2.11). All keys camelCase (D-076).

/** DiffCard discriminator — UPPERCASE on the wire (Jackson @JsonTypeInfo name). */
export type CardType = 'EXISTING' | 'NEW' | 'UNCERTAIN'

/** Entity category — LOWERCASE on the wire (differs from CardType per §7.6). */
export type EntityType = 'actor' | 'space' | 'event' | 'relation'

/** Entity already in world state; shows the changed fields only (D-006). */
export interface ExistingDiffCard {
  cardId: string
  cardType: 'EXISTING'
  entityId: string
  entityType: EntityType
  name: string
  changedFields: Record<string, unknown>
}

/** Entity appearing for the first time; shows the full extracted profile (D-007). */
export interface NewDiffCard {
  cardId: string
  cardType: 'NEW'
  entityType: EntityType
  name: string
  fullProfile: Record<string, unknown>
}

/** AI could not decide match-vs-new; user must resolve MATCH or NEW (D-042). */
export interface UncertainDiffCard {
  cardId: string
  cardType: 'UNCERTAIN'
  entityType: EntityType
  extractedMention: string
  candidateEntityId: string
  candidateEntityName: string
}

export type DiffCard = ExistingDiffCard | NewDiffCard | UncertainDiffCard

/** A detected contradiction. Non-blocking but must be acknowledged (D-033). Not part of the DiffCard union (no `cardType`). */
export interface ConflictCard {
  conflictId: string
  entityId: string
  entityType: EntityType
  description: string
  extractedFact: string
  existingFact: string
}

export interface DiffPayload {
  narrativeSummaryHeader: string
  actors: DiffCard[]
  spaces: DiffCard[]
  events: DiffCard[]
  relations: DiffCard[]
  detectedConflicts: ConflictCard[]
}

// ─── Commit (ARCHITECTURE §7.6) ──────────────────────────────────────────────
// Derived write shape POSTed to .../commit. Mirrors the F2.8 CommitSessionRequest
// exactly. All keys camelCase (D-076).

/** Per-card decision. `editedFields` is present + non-empty only when `action === 'edit'`. */
export interface CardDecisionPayload {
  cardId: string
  action: 'accept' | 'edit' | 'delete'
  editedFields?: Record<string, unknown>
}

/** Resolution of an UNCERTAIN card. `matchedEntityId` is non-null only for MATCH. */
export interface UncertainResolutionPayload {
  cardId: string
  resolution: 'MATCH' | 'NEW'
  matchedEntityId: string | null
}

export interface AcknowledgedConflictPayload {
  conflictId: string
}

export interface CommitPayload {
  cardDecisions: CardDecisionPayload[]
  uncertainResolutions: UncertainResolutionPayload[]
  acknowledgedConflicts: AcknowledgedConflictPayload[]
}
