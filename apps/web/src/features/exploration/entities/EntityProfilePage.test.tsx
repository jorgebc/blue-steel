import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import type { UseQueryResult } from '@tanstack/react-query'
import { EntityProfilePage } from './EntityProfilePage'
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
  entityId: 'a1',
  entityType: 'actor',
  name: 'Aldric',
  ownerId: 'u1',
  createdAt: '2026-01-01T09:00:00Z',
  versions: [
    {
      versionId: 'v1',
      versionNumber: 1,
      sessionId: 's1',
      sessionSequenceNumber: 1,
      changedFields: {},
      fullSnapshot: { role: 'squire' },
      createdAt: '2026-01-01T09:00:00Z',
    },
    {
      versionId: 'v2',
      versionNumber: 2,
      sessionId: 's2',
      sessionSequenceNumber: 3,
      changedFields: { role: 'knight' },
      fullSnapshot: { role: 'knight', title: 'Champion of Light' },
      createdAt: '2026-01-02T09:00:00Z',
    },
  ],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/explore/entities/a1']}>
      <Routes>
        <Route
          path="/campaigns/:campaignId/explore/entities/:entityId"
          element={<EntityProfilePage />}
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

describe('EntityProfilePage', () => {
  it('requests the actor detail for the route id', () => {
    renderPage()
    expect(mockUseEntityDetail).toHaveBeenCalledWith('actor', 'a1')
  })

  it('renders the name and the latest snapshot as current state', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: 'Aldric' })).toBeInTheDocument()
    expect(screen.getByText('Current state')).toBeInTheDocument()
    // 'Champion of Light' appears only in the latest snapshot, confirming current state renders it.
    expect(screen.getByText('Champion of Light')).toBeInTheDocument()
  })

  it('renders the version history', () => {
    renderPage()

    expect(screen.getByText('Version history')).toBeInTheDocument()
    expect(screen.getByText('Changed: role')).toBeInTheDocument()
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

    expect(screen.getByRole('alert')).toHaveTextContent(/could not load/i)
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
