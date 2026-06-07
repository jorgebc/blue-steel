import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import type { UseQueryResult } from '@tanstack/react-query'
import { RelationDetailPage } from './RelationDetailPage'
import { useRelationDetail } from '@/api/relations'
import type { RelationDetail } from '@/types/relation'

vi.mock('@/api/relations', () => ({ useRelationDetail: vi.fn() }))
const mockUseRelationDetail = vi.mocked(useRelationDetail)

const detail: RelationDetail = {
  relationId: 'r1',
  name: 'Aldric trusts Mira',
  kind: 'trust',
  sourceEntityId: 'a1',
  sourceEntityType: 'actor',
  targetEntityId: 'x1',
  targetEntityType: 'space',
  ownerId: 'u1',
  createdAt: '2026-01-01T09:00:00Z',
  versions: [
    {
      versionId: 'v1',
      versionNumber: 1,
      sessionId: 's1',
      sessionSequenceNumber: 1,
      changedFields: {},
      fullSnapshot: { kind: 'trust' },
      createdAt: '2026-01-01T09:00:00Z',
    },
  ],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/explore/relations/r1']}>
      <Routes>
        <Route
          path="/campaigns/:campaignId/explore/relations/:relationId"
          element={<RelationDetailPage />}
        />
      </Routes>
    </MemoryRouter>
  )
}

function mockResult(over: Partial<UseQueryResult<RelationDetail>>) {
  mockUseRelationDetail.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    ...over,
  } as unknown as UseQueryResult<RelationDetail>)
}

beforeEach(() => {
  vi.clearAllMocks()
  mockResult({ data: detail })
})

describe('RelationDetailPage', () => {
  it('requests the relation detail for the route id', () => {
    renderPage()
    expect(mockUseRelationDetail).toHaveBeenCalledWith('r1')
  })

  it('renders the relation name and kind', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: 'Aldric trusts Mira' })).toBeInTheDocument()
    expect(screen.getByText('trust')).toBeInTheDocument()
  })

  it('links each resolved endpoint to its entity profile by type', () => {
    renderPage()

    expect(screen.getByRole('link', { name: /actor/i })).toHaveAttribute(
      'href',
      '/campaigns/c1/explore/entities/a1'
    )
    expect(screen.getByRole('link', { name: /space/i })).toHaveAttribute(
      'href',
      '/campaigns/c1/explore/spaces/x1'
    )
  })

  it('renders an unresolved endpoint as non-navigable text', () => {
    mockResult({ data: { ...detail, targetEntityId: null, targetEntityType: null } })
    renderPage()

    expect(screen.getByText('Unresolved')).toBeInTheDocument()
    // Only the source endpoint remains a link.
    expect(screen.queryByRole('link', { name: /space/i })).not.toBeInTheDocument()
  })

  it('renders a back link to the relations view', () => {
    renderPage()

    expect(screen.getByRole('link', { name: /back to relations/i })).toHaveAttribute(
      'href',
      '/campaigns/c1/explore/relations'
    )
  })

  it('shows the loading skeleton while fetching', () => {
    mockResult({ isLoading: true })
    renderPage()

    expect(screen.getByRole('status', { name: /loading profile/i })).toBeInTheDocument()
  })

  it('shows an error banner when the request fails', () => {
    mockResult({ isError: true })
    renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent(/could not load this relation/i)
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
