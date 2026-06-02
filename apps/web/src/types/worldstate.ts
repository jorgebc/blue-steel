/**
 * Hand-written mirrors of the backend world-state read DTOs (F4.1.4). Snapshots are freeform,
 * entity-type-specific JSON objects and are typed as `Record<string, unknown>` — render generically.
 */

export type EntityType = 'actor' | 'space' | 'event' | 'relation'

export interface EntitySummary {
  entityId: string
  entityType: EntityType
  name: string
  latestVersionNumber: number
  currentSnapshot: Record<string, unknown>
  lastUpdatedSessionId: string | null
  createdAt: string
}

export interface EntityVersion {
  versionId: string
  versionNumber: number
  sessionId: string
  sessionSequenceNumber: number | null
  changedFields: Record<string, unknown>
  fullSnapshot: Record<string, unknown>
  createdAt: string
}

export interface EntityDetail {
  entityId: string
  entityType: EntityType
  name: string
  ownerId: string
  createdAt: string
  versions: EntityVersion[]
}

/** One offset-paginated page of entity summaries plus the `meta` paging fields (D-055). */
export interface EntityListPage {
  items: EntitySummary[]
  page: number
  size: number
  totalCount: number
}
