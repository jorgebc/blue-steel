import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import type { UseQueryResult } from '@tanstack/react-query'
import { SpaceProfilePage } from './SpaceProfilePage'
import { useEntityDetail } from '@/api/worldstate'
import type { EntityDetail } from '@/types/worldstate'

vi.mock('@/api/worldstate', () => ({ useEntityDetail: vi.fn() }))
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

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
