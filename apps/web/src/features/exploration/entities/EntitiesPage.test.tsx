import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import type { UseQueryResult } from '@tanstack/react-query'
import { EntitiesPage } from './EntitiesPage'
import { useEntityList } from '@/api/worldstate'
import type { EntityListPage } from '@/types/worldstate'
import i18n from '@/i18n'

vi.mock('@/api/worldstate', () => ({ useEntityList: vi.fn() }))
const mockUseEntityList = vi.mocked(useEntityList)

const page: EntityListPage = {
  items: [
    {
      entityId: 'a1',
      entityType: 'actor',
      name: 'Aldric',
      latestVersionNumber: 2,
      currentSnapshot: {},
      lastUpdatedSessionId: 's2',
      createdAt: '2026-01-01T09:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalCount: 1,
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/explore/entities']}>
      <Routes>
        <Route path="/campaigns/:campaignId/explore/entities" element={<EntitiesPage />} />
      </Routes>
    </MemoryRouter>
  )
}

function mockResult(over: Partial<UseQueryResult<EntityListPage>>) {
  mockUseEntityList.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    ...over,
  } as unknown as UseQueryResult<EntityListPage>)
}

beforeEach(() => {
  vi.clearAllMocks()
  mockResult({ data: page })
})

describe('EntitiesPage', () => {
  it('renders the actor rows linking to their profiles', () => {
    renderPage()

    const link = screen.getByRole('link', { name: /Aldric/i })
    expect(link).toHaveAttribute('href', '/campaigns/c1/explore/entities/a1')
    expect(screen.getByText('v2')).toBeInTheDocument()
  })

  it('shows the loading skeleton while fetching', () => {
    mockResult({ isLoading: true })
    renderPage()

    expect(screen.getByRole('status', { name: /loading entities/i })).toBeInTheDocument()
  })

  it('shows an error banner when the request fails', () => {
    mockResult({ isError: true })
    renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent(/could not load/i)
  })

  it('shows an empty state when there are no entities', () => {
    mockResult({ data: { items: [], page: 0, size: 20, totalCount: 0 } })
    renderPage()

    expect(screen.getByText(/nothing here yet/i)).toBeInTheDocument()
  })

  it('passes the debounced search term to the query', async () => {
    renderPage()

    await userEvent.type(screen.getByRole('searchbox', { name: /search entities by name/i }), 'ald')

    await waitFor(() => expect(mockUseEntityList).toHaveBeenLastCalledWith('actor', 0, 'ald'))
  })

  it('shows a no-match message when a search returns nothing', async () => {
    mockResult({ data: { items: [], page: 0, size: 20, totalCount: 0 } })
    renderPage()

    await userEvent.type(screen.getByRole('searchbox'), 'zzz')

    await waitFor(() => expect(screen.getByText(/no entities match/i)).toBeInTheDocument())
  })

  it('disables Previous on the first page and Next when there is a single page', () => {
    renderPage()

    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /next/i })).toBeDisabled()
  })

  it('enables Next when more pages exist and advances the page', async () => {
    mockResult({ data: { ...page, totalCount: 40 } })
    renderPage()

    const next = screen.getByRole('button', { name: /next/i })
    expect(next).toBeEnabled()
    await userEvent.click(next)

    expect(mockUseEntityList).toHaveBeenLastCalledWith('actor', 1, '')
  })

  it('renders the localized title and description in English', () => {
    renderPage()

    expect(screen.getByText('Entities')).toBeInTheDocument()
    expect(screen.getByText("Actors recorded across this campaign's sessions.")).toBeInTheDocument()
  })

  it('renders the title and description in Spanish when the UI locale is es', async () => {
    await i18n.changeLanguage('es')
    renderPage()

    expect(screen.getByText('Entidades')).toBeInTheDocument()
    expect(
      screen.getByText('Actores registrados en las sesiones de esta campaña.')
    ).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
