import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { createElement, type ReactNode } from 'react'
import { apiClient } from './client'
import {
  annotationKeys,
  getAnnotations,
  postAnnotation,
  deleteAnnotation,
  useAnnotations,
  usePostAnnotation,
  useDeleteAnnotation,
} from './annotations'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import type { Annotation, CreateAnnotationRequest } from '@/types/annotation'

vi.mock('./client', () => ({
  apiClient: { get: vi.fn(), post: vi.fn(), delete: vi.fn() },
}))

function envelope<T>(data: T) {
  return { data, meta: null, errors: [] }
}

function wrapper({ children }: { children: ReactNode }) {
  return createElement(QueryClientProvider, { client: createTestQueryClient() }, children)
}

const annotation: Annotation = {
  id: 'an1',
  entityType: 'actor',
  entityId: 'e1',
  authorId: 'u1',
  content: 'A suspicious figure.',
  createdAt: '2026-06-05T10:30:00Z',
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('annotationKeys', () => {
  it('scopes the entity key under the campaign annotations key', () => {
    expect(annotationKeys.all('c1')).toEqual(['annotations', 'c1'])
    expect(annotationKeys.byEntity('c1', 'actor', 'e1')).toEqual([
      'annotations',
      'c1',
      'actor',
      'e1',
    ])
  })
})

describe('getAnnotations', () => {
  it('requests the entity-scoped list with entityType and entityId query params', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope([annotation]))

    const result = await getAnnotations('c1', 'actor', 'e1')

    expect(apiClient.get).toHaveBeenCalledWith(
      '/api/v1/campaigns/c1/annotations?entityType=actor&entityId=e1'
    )
    expect(result).toEqual([annotation])
  })
})

describe('postAnnotation', () => {
  it('posts the camelCase create body and returns the created annotation', async () => {
    const body: CreateAnnotationRequest = { entityType: 'actor', entityId: 'e1', content: 'Hi' }
    vi.mocked(apiClient.post).mockResolvedValue(envelope(annotation))

    const result = await postAnnotation('c1', body)

    expect(apiClient.post).toHaveBeenCalledWith('/api/v1/campaigns/c1/annotations', body)
    expect(result).toEqual(annotation)
  })
})

describe('deleteAnnotation', () => {
  it('deletes by annotation id', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue(envelope(null))

    await deleteAnnotation('c1', 'an1')

    expect(apiClient.delete).toHaveBeenCalledWith('/api/v1/campaigns/c1/annotations/an1')
  })
})

describe('useAnnotations', () => {
  it('returns the annotation list on success', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope([annotation]))

    const { result } = renderHook(() => useAnnotations('c1', 'actor', 'e1'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual([annotation])
  })

  it('does not fetch when the entity id is empty', () => {
    renderHook(() => useAnnotations('c1', 'actor', ''), { wrapper })

    expect(apiClient.get).not.toHaveBeenCalled()
  })
})

describe('usePostAnnotation', () => {
  it('invalidates the entity annotation list on success', async () => {
    vi.mocked(apiClient.post).mockResolvedValue(envelope(annotation))
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const localWrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children)

    const { result } = renderHook(() => usePostAnnotation('c1'), { wrapper: localWrapper })
    result.current.mutate({ entityType: 'actor', entityId: 'e1', content: 'Hi' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: annotationKeys.byEntity('c1', 'actor', 'e1'),
    })
  })
})

describe('useDeleteAnnotation', () => {
  it('invalidates the entity annotation list on success', async () => {
    vi.mocked(apiClient.delete).mockResolvedValue(envelope(null))
    const queryClient = createTestQueryClient()
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries')
    const localWrapper = ({ children }: { children: ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children)

    const { result } = renderHook(() => useDeleteAnnotation('c1'), { wrapper: localWrapper })
    result.current.mutate({ annotationId: 'an1', entityType: 'actor', entityId: 'e1' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(apiClient.delete).toHaveBeenCalledWith('/api/v1/campaigns/c1/annotations/an1')
    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: annotationKeys.byEntity('c1', 'actor', 'e1'),
    })
  })
})
