/**
 * Hand-written mirror of the backend Timeline read DTOs (F4.2.4). The feed is keyset-paginated
 * (D-055): each page carries an opaque `nextCursor`, or `null` when the feed is exhausted.
 * `eventType` and `spaceName` may be null and `involvedActorNames` empty until the extraction
 * pipeline enriches event snapshots.
 */

export interface TimelineEvent {
  eventId: string
  name: string
  eventType: string | null
  involvedActorNames: string[]
  spaceName: string | null
  sessionId: string
  sessionSequenceNumber: number
  createdAt: string
}

export interface TimelinePage {
  events: TimelineEvent[]
  nextCursor: string | null
}
