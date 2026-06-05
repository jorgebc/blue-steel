import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from './client'
import type { Annotation, AnnotationEntityType, CreateAnnotationRequest } from '@/types/annotation'

/** Query-key factory for annotation reads, scoped per campaign + annotated entity. */
export const annotationKeys = {
  all: (campaignId: string) => ['annotations', campaignId] as const,
  byEntity: (campaignId: string, entityType: AnnotationEntityType, entityId: string) =>
    [...annotationKeys.all(campaignId), entityType, entityId] as const,
}

/** Lists every annotation attached to a single entity (F4.4.4). */
export async function getAnnotations(
  campaignId: string,
  entityType: AnnotationEntityType,
  entityId: string
): Promise<Annotation[]> {
  const res = await apiClient.get<Annotation[]>(
    `/api/v1/campaigns/${campaignId}/annotations?entityType=${entityType}&entityId=${entityId}`
  )
  return res.data
}

/** Posts a new annotation. Any campaign member may create (D-011). */
export async function postAnnotation(
  campaignId: string,
  body: CreateAnnotationRequest
): Promise<Annotation> {
  const res = await apiClient.post<Annotation>(`/api/v1/campaigns/${campaignId}/annotations`, body)
  return res.data
}

/** Deletes an annotation. Backend enforces author-or-GM (403 otherwise). */
export async function deleteAnnotation(campaignId: string, annotationId: string): Promise<void> {
  await apiClient.delete<null>(`/api/v1/campaigns/${campaignId}/annotations/${annotationId}`)
}

/** All annotations for one entity in the given campaign. */
export function useAnnotations(
  campaignId: string,
  entityType: AnnotationEntityType,
  entityId: string
) {
  return useQuery({
    queryKey: annotationKeys.byEntity(campaignId, entityType, entityId),
    queryFn: () => getAnnotations(campaignId, entityType, entityId),
    enabled: campaignId !== '' && entityId !== '',
  })
}

/** Posts an annotation, then refreshes the affected entity's annotation list. */
export function usePostAnnotation(campaignId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateAnnotationRequest) => postAnnotation(campaignId, body),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: annotationKeys.byEntity(campaignId, variables.entityType, variables.entityId),
      })
    },
  })
}

/** Variables for {@link useDeleteAnnotation}: the entity ids are carried so the list cache key can
 * be invalidated (the DELETE endpoint itself takes only the annotation id). */
export interface DeleteAnnotationVariables {
  annotationId: string
  entityType: AnnotationEntityType
  entityId: string
}

/** Deletes an annotation, then refreshes the affected entity's annotation list. */
export function useDeleteAnnotation(campaignId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (variables: DeleteAnnotationVariables) =>
      deleteAnnotation(campaignId, variables.annotationId),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: annotationKeys.byEntity(campaignId, variables.entityType, variables.entityId),
      })
    },
  })
}
