/**
 * Hand-written mirrors of the backend relation read DTOs (F4.3.6) plus the React Flow graph helper
 * types. A relation's `sourceEntityId`/`targetEntityId` are null when the endpoint could not be
 * name-matched at commit (D-095); such relations cannot be drawn as edges and surface in the
 * accessible list instead. `kind` is nullable — the edge label falls back to `name`.
 */

import type { EntityVersion } from './worldstate'

export interface Relation {
  relationId: string
  name: string
  kind: string | null
  sourceEntityId: string | null
  sourceEntityType: string | null
  targetEntityId: string | null
  targetEntityType: string | null
  sessionId: string | null
}

export interface RelationDetail {
  relationId: string
  name: string
  kind: string | null
  sourceEntityId: string | null
  sourceEntityType: string | null
  targetEntityId: string | null
  targetEntityType: string | null
  ownerId: string
  createdAt: string
  versions: EntityVersion[]
}

/** Data carried by a React Flow node — a graph-rendered actor or space. */
export interface NodeData {
  entityId: string
  entityType: 'actor' | 'space'
  name: string
  [key: string]: unknown
}

/** Data carried by a React Flow edge — a relation connecting two nodes. */
export interface EdgeData {
  relationId: string
  label: string
  [key: string]: unknown
}
