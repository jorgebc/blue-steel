// Mirrors the backend annotation DTOs (AnnotationController / AnnotationResponse /
// CreateAnnotationRequest). Annotations are non-canonical and immutable (D-011) — there is
// deliberately no `updatedAt` field.

/** The world-state surfaces an annotation can be attached to (backend `entity_type` whitelist). */
export type AnnotationEntityType = 'actor' | 'space' | 'event' | 'relation'

/** Mirror of the backend `AnnotationResponse`. */
export interface Annotation {
  id: string
  entityType: AnnotationEntityType
  entityId: string
  authorId: string
  content: string
  createdAt: string // ISO 8601 UTC
}

/** Mirror of the backend `CreateAnnotationRequest` (camelCase body, D-076). */
export interface CreateAnnotationRequest {
  entityType: AnnotationEntityType
  entityId: string
  content: string
}
