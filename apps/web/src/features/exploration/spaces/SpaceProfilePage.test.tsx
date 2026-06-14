import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import type { UseQueryResult } from '@tanstack/react-query'
import { SpaceProfilePage } from './SpaceProfilePage'
import { useEntityDetail } from '@/api/worldstate'
import type { EntityDetail } from '@/types/worldstate'

vi.mock('@/api/worldstate', () => ({
  useEntityDetail: vi.fn(),
  useEntityLinks: () => ({
    data: { relations: [], relatedEntities: [], events: [], appearances: [] },
    isLoading: false,
    isError: false,
  }),
}))
vi.mock('@/api/annotations', () => ({
  useAnnotations: () => ({ data: [], isLoading: false, isError: false }),
  usePostAnnotation: () => ({ mutate: vi.fn(), isPending: false }),
  useDeleteAnnotation: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/api/proposals', () => ({
  useProposals: () => ({
    data: { proposals: [], page: 0, size: 20, totalCount: 0 },
    isLoading: false,
    isError: false,
  }),
  useCoSignProposal: () => ({ mutate: vi.fn(), isPending: false }),
  useCreateProposal: () => ({ mutate: vi.fn(), isPending: false }),
}))
vi.mock('@/api/sessions', () => ({ useSessions: () => ({ data: [] }) }))
const mockUseEntityDetail = vi.mocked(useEntityDetail)

const detail: EntityDetail = {
  entityId: 'x1',
  entityType: 'space',
  name: 'The Prancing Pony',
  ownerId: 'u1',
  createdAt: '2026-01-01T09:00:00Z',
  versions: [
    {
      versionId: 'v1',
      versionNumber: 1,
      sessionId: 's1',
      sessionSequenceNumber: 1,
      changedFields: {},
      fullSnapshot: { region: 'Bree' },
      createdAt: '2026-01-01T09:00:00Z',
    },
  ],
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/explore/spaces/x1']}>
      <Routes>
        <Route
          path="/campaigns/:campaignId/explore/spaces/:spaceId"
          element={<SpaceProfilePage />}
        />
      </Routes>
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockUseEntityDetail.mockReturnValue({
    data: detail,
    isLoading: false,
    isError: false,
  } as unknown as UseQueryResult<EntityDetail>)
})

describe('SpaceProfilePage', () => {
  it('requests the space detail for the route id and renders its state', () => {
    renderPage()

    expect(mockUseEntityDetail).toHaveBeenCalledWith('space', 'x1')
    expect(screen.getByRole('heading', { name: 'The Prancing Pony' })).toBeInTheDocument()
    expect(screen.getByText('Bree')).toBeInTheDocument()
  })

  it('renders an active propose-change affordance (F5.7)', () => {
    renderPage()
    expect(screen.getByRole('button', { name: /propose a change/i })).toBeEnabled()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
