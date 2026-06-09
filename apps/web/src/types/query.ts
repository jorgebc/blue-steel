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
