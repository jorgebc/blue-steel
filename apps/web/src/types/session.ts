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
