import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { AnnotationThread } from './AnnotationThread'
import { useAnnotations, usePostAnnotation, useDeleteAnnotation } from '@/api/annotations'
import { useAuthStore } from '@/store/authStore'
import { useCampaignStore } from '@/store/campaignStore'
import type { Annotation } from '@/types/annotation'
import type { CampaignRole } from '@/types/campaign'

vi.mock('@/api/annotations')

const mockUseAnnotations = vi.mocked(useAnnotations)
const mockUsePostAnnotation = vi.mocked(usePostAnnotation)
const mockUseDeleteAnnotation = vi.mocked(useDeleteAnnotation)

const authorId = 'author-0000-0000-0000-000000000000'
const otherId = 'other-0000-0000-0000-0000000000000'

const annotation: Annotation = {
  id: 'an1',
  entityType: 'actor',
  entityId: 'e1',
  authorId,
  content: 'A suspicious figure.',
  createdAt: '2026-06-05T10:30:00Z',
}

const postMutate = vi.fn()
const deleteMutate = vi.fn()

function setStores(currentUserId: string | null, role: CampaignRole | null) {
  useCampaignStore.getState().setCampaign('c1', role)
  useAuthStore
    .getState()
    .setCurrentUser(
      currentUserId
        ? {
            id: currentUserId,
            email: 'x@y.z',
            isAdmin: false,
            forcePasswordChange: false,
            displayName: null,
            avatarAccentColor: null,
            uiLocale: 'en',
            theme: 'system',
          }
        : null
    )
}

function mockList(over: Partial<ReturnType<typeof useAnnotations>> = {}) {
  mockUseAnnotations.mockReturnValue({
    data: [annotation],
    isLoading: false,
    isError: false,
    ...over,
  } as ReturnType<typeof useAnnotations>)
}

beforeEach(() => {
  vi.clearAllMocks()
  setStores(authorId, 'player')
  mockList()
  mockUsePostAnnotation.mockReturnValue({
    mutate: postMutate,
    isPending: false,
  } as unknown as ReturnType<typeof usePostAnnotation>)
  mockUseDeleteAnnotation.mockReturnValue({
    mutate: deleteMutate,
    isPending: false,
  } as unknown as ReturnType<typeof useDeleteAnnotation>)
})

describe('AnnotationThread', () => {
  it('renders fetched annotations and marks the section as non-canonical', () => {
    render(<AnnotationThread entityType="actor" entityId="e1" />)
    expect(screen.getByText(/suspicious figure/i)).toBeInTheDocument()
    expect(screen.getByText(/not canonical world state/i)).toBeInTheDocument()
  })

  it('shows the empty state when there are no annotations', () => {
    mockList({ data: [] })
    render(<AnnotationThread entityType="actor" entityId="e1" />)
    expect(screen.getByText(/no annotations yet/i)).toBeInTheDocument()
  })

  it('lets the author delete their own annotation', () => {
    setStores(authorId, 'player')
    render(<AnnotationThread entityType="actor" entityId="e1" />)
    expect(screen.getByRole('button', { name: /delete annotation/i })).toBeInTheDocument()
  })

  it('hides delete from a non-author non-GM member', () => {
    setStores(otherId, 'player')
    render(<AnnotationThread entityType="actor" entityId="e1" />)
    expect(screen.queryByRole('button', { name: /delete annotation/i })).not.toBeInTheDocument()
  })

  it('lets a GM delete any annotation', () => {
    setStores(otherId, 'gm')
    render(<AnnotationThread entityType="actor" entityId="e1" />)
    expect(screen.getByRole('button', { name: /delete annotation/i })).toBeInTheDocument()
  })

  it('posts a new annotation and shows success feedback', async () => {
    postMutate.mockImplementation((_vars, opts) => opts?.onSuccess?.())
    render(<AnnotationThread entityType="actor" entityId="e1" />)

    await userEvent.type(screen.getByRole('textbox', { name: /add an annotation/i }), 'new note')
    await userEvent.click(screen.getByRole('button', { name: /post annotation/i }))

    expect(postMutate).toHaveBeenCalledWith(
      { entityType: 'actor', entityId: 'e1', content: 'new note' },
      expect.objectContaining({ onSuccess: expect.any(Function) })
    )
    expect(screen.getByText(/annotation posted/i)).toBeInTheDocument()
  })

  it('opens a confirm overlay and deletes on confirm', async () => {
    deleteMutate.mockImplementation((_vars, opts) => opts?.onSuccess?.())
    render(<AnnotationThread entityType="actor" entityId="e1" />)

    await userEvent.click(screen.getByRole('button', { name: /delete annotation/i }))

    const dialog = screen.getByRole('dialog', { name: /delete annotation/i })
    await userEvent.click(within(dialog).getByRole('button', { name: /^delete$/i }))

    expect(deleteMutate).toHaveBeenCalledWith(
      { annotationId: 'an1', entityType: 'actor', entityId: 'e1' },
      expect.objectContaining({ onSuccess: expect.any(Function) })
    )
    expect(screen.getByText(/annotation deleted/i)).toBeInTheDocument()
  })

  it('shows an error banner when the list fails to load', () => {
    mockList({ data: undefined, isError: true })
    render(<AnnotationThread entityType="actor" entityId="e1" />)
    expect(screen.getByText(/could not load annotations/i)).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<AnnotationThread entityType="actor" entityId="e1" />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
