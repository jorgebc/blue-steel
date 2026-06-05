import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import type { UseQueryResult } from '@tanstack/react-query'
import { EventDetailPage } from './EventDetailPage'
import { useEntityDetail } from '@/api/worldstate'
import type { EntityDetail } from '@/types/worldstate'

vi.mock('@/api/worldstate', () => ({ useEntityDetail: vi.fn() }))
vi.mock('@/api/annotations', () => ({
  useAnnotations: () => ({ data: [], isLoading: false, isError: false }),
  usePostAnnotation: () => ({ mutate: vi.fn(), isPending: false }),
  useDeleteAnnotation: () => ({ mutate: vi.fn(), isPending: false }),
}))
const mockUseEntityDetail = vi.mocked(useEntityDetail)

const detail: EntityDetail = {
  entityId: 'e1',
  entityType: 'event',
  name: 'Ambush at the Pass',
  ownerId: 'u1',
  createdAt: '2026-01-01T09:00:00Z',
  versions: [
    {
      versionId: 'v1',
      versionNumber: 1,
      sessionId: 's1',
      sessionSequenceNumber: 1,
      changedFields: {},
      fullSnapshot: { description: 'A surprise attack', eventType: 'battle' },
      createdAt: '2026-01-01T09:00:00Z',
    },
  ],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/explore/events/e1']}>
      <Routes>
        <Route
          path="/campaigns/:campaignId/explore/events/:eventId"
          element={<EventDetailPage />}
        />
      </Routes>
    </MemoryRouter>
  )
}

function mockResult(over: Partial<UseQueryResult<EntityDetail>>) {
  mockUseEntityDetail.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    ...over,
  } as unknown as UseQueryResult<EntityDetail>)
}

beforeEach(() => {
  vi.clearAllMocks()
  mockResult({ data: detail })
})

describe('EventDetailPage', () => {
  it('requests the event detail for the route id', () => {
    renderPage()
    expect(mockUseEntityDetail).toHaveBeenCalledWith('event', 'e1')
  })

  it('renders the event name and its latest snapshot as current state', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: 'Ambush at the Pass' })).toBeInTheDocument()
    expect(screen.getByText('Current state')).toBeInTheDocument()
    expect(screen.getByText('A surprise attack')).toBeInTheDocument()
  })

  it('renders a back link to the timeline', () => {
    renderPage()

    expect(screen.getByRole('link', { name: /back to timeline/i })).toHaveAttribute(
      'href',
      '/campaigns/c1/explore/timeline'
    )
  })

  it('renders the propose-change affordance as a disabled stub (D-012)', () => {
    renderPage()

    expect(screen.getByRole('button', { name: /propose a change/i })).toBeDisabled()
  })

  it('shows the loading skeleton while fetching', () => {
    mockResult({ isLoading: true })
    renderPage()

    expect(screen.getByRole('status', { name: /loading profile/i })).toBeInTheDocument()
  })

  it('shows an error banner when the request fails', () => {
    mockResult({ isError: true })
    renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent(/could not load this event/i)
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
