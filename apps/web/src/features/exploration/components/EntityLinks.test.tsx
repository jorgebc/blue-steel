import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import type { UseQueryResult } from '@tanstack/react-query'
import { EntityLinks } from './EntityLinks'
import { useEntityLinks } from '@/api/worldstate'
import type { EntityLinks as EntityLinksData } from '@/types/worldstate'

vi.mock('@/api/worldstate', () => ({ useEntityLinks: vi.fn() }))
const mockUseEntityLinks = vi.mocked(useEntityLinks)

const links: EntityLinksData = {
  relations: [
    {
      relationId: 'r1',
      name: 'Aldric guards the Tavern',
      kind: 'guardianship',
      sourceEntityId: 'a1',
      sourceEntityType: 'actor',
      targetEntityId: 'x1',
      targetEntityType: 'space',
      sessionId: 's1',
    },
  ],
  relatedEntities: [
    {
      entityId: 'x1',
      entityType: 'space',
      name: 'The Tavern',
      latestVersionNumber: 1,
      currentSnapshot: {},
      lastUpdatedSessionId: 's1',
      createdAt: '2026-01-01T09:00:00Z',
    },
  ],
  events: [
    {
      eventId: 'e1',
      name: 'The Brawl',
      eventType: 'conflict',
      involvedActorNames: ['Aldric'],
      spaceName: 'The Tavern',
      sessionId: 's1',
      sessionSequenceNumber: 1,
      createdAt: '2026-01-01T09:00:00Z',
    },
  ],
  appearanceSessionIds: ['11111111-aaaa', '22222222-bbbb'],
}

function renderLinks() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/explore/entities/a1']}>
      <Routes>
        <Route
          path="/campaigns/:campaignId/explore/entities/:entityId"
          element={<EntityLinks entityType="actor" entityId="a1" />}
        />
      </Routes>
    </MemoryRouter>
  )
}

function mockResult(over: Partial<UseQueryResult<EntityLinksData>>) {
  mockUseEntityLinks.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    ...over,
  } as unknown as UseQueryResult<EntityLinksData>)
}

beforeEach(() => {
  vi.clearAllMocks()
  mockResult({ data: links })
})

describe('EntityLinks', () => {
  it('renders relation rows with their kind', () => {
    renderLinks()

    expect(screen.getByText('Aldric guards the Tavern')).toBeInTheDocument()
    expect(screen.getByText('guardianship')).toBeInTheDocument()
  })

  it('links each related entity to its profile by type', () => {
    renderLinks()

    // Anchored to the start of the accessible name so it does not also match the event card,
    // whose name contains "The Tavern" as the event's space.
    const link = screen.getByRole('link', { name: /^The Tavern/i })
    expect(link).toHaveAttribute('href', '/campaigns/c1/explore/spaces/x1')
  })

  it('links each event to its detail page via EventCard', () => {
    renderLinks()

    const link = screen.getByRole('link', { name: /The Brawl/i })
    expect(link).toHaveAttribute('href', '/campaigns/c1/explore/events/e1')
  })

  it('shows the appearance session count', () => {
    renderLinks()

    expect(screen.getByText('Appears in 2 sessions')).toBeInTheDocument()
  })

  it('shows the loading skeleton while fetching', () => {
    mockResult({ isLoading: true })
    renderLinks()

    expect(screen.getByRole('status', { name: /loading connections/i })).toBeInTheDocument()
  })

  it('shows an error banner when the request fails', () => {
    mockResult({ isError: true })
    renderLinks()

    expect(screen.getByRole('alert')).toHaveTextContent(/could not load connections/i)
  })

  it('shows per-section empty states when there are no links', () => {
    mockResult({
      data: { relations: [], relatedEntities: [], events: [], appearanceSessionIds: [] },
    })
    renderLinks()

    expect(screen.getByText('No relations.')).toBeInTheDocument()
    expect(screen.getByText('No related entities.')).toBeInTheDocument()
    expect(screen.getByText('No events.')).toBeInTheDocument()
    expect(screen.getByText('Does not appear in any sessions.')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderLinks()
    expect(await axe(container)).toHaveNoViolations()
  })
})
