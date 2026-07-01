import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import type { UseQueryResult } from '@tanstack/react-query'
import { SpacesPage } from './SpacesPage'
import { useEntityList } from '@/api/worldstate'
import type { EntityListPage } from '@/types/worldstate'

vi.mock('@/api/worldstate', () => ({ useEntityList: vi.fn() }))
const mockUseEntityList = vi.mocked(useEntityList)

const page: EntityListPage = {
  items: [
    {
      entityId: 'x1',
      entityType: 'space',
      name: 'The Prancing Pony',
      latestVersionNumber: 1,
      currentSnapshot: {},
      lastUpdatedSessionId: 's1',
      createdAt: '2026-01-01T09:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalCount: 1,
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/explore/spaces']}>
      <Routes>
        <Route path="/campaigns/:campaignId/explore/spaces" element={<SpacesPage />} />
      </Routes>
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockUseEntityList.mockReturnValue({
    data: page,
    isLoading: false,
    isError: false,
  } as unknown as UseQueryResult<EntityListPage>)
})

describe('SpacesPage', () => {
  it('lists spaces via the space entity type, linking to space profiles', () => {
    renderPage()

    expect(mockUseEntityList).toHaveBeenCalledWith('space', 0, '')
    expect(screen.getByRole('link', { name: /Prancing Pony/i })).toHaveAttribute(
      'href',
      '/campaigns/c1/explore/spaces/x1'
    )
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
