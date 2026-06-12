// Query Mode DTOs — hand-maintained mirrors of the backend query records (F3.1–F3.3).
// Envelope `{ data, meta, errors }` is unwrapped by the API client; these type the `data`.

export interface QueryRequest {
  question: string
}

/**
 * A single grounded citation backing the answer. `snippet` is the supporting excerpt the
 * backend attributes to the cited session (D-003). `sequenceNumber` is the session's
 * human-facing ordinal.
 */
export interface Citation {
  sessionId: string
  sequenceNumber: number
  snippet: string
}

export interface QueryResponse {
  answer: string
  citations: Citation[]
}

/**
 * Shared Query Mode usage against the free-tier guards (D-096). `consumedUsd`/`capUsd` are the
 * instance-wide daily LLM budget; `requestsRemaining` is how many more questions the caller may ask
 * in the current `windowSeconds` window before the per-(user,campaign) rate limit of `maxRequests`.
 */
export interface QueryUsage {
  consumedUsd: number
  capUsd: number
  requestsRemaining: number
  maxRequests: number
  windowSeconds: number
}
