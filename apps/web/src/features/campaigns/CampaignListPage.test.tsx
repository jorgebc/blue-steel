import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { CampaignListPage } from './CampaignListPage'
import { useCampaigns } from '@/api/campaigns'
import { useAuthStore } from '@/store/authStore'
import type { CampaignResponse } from '@/types/campaign'

vi.mock('@/api/campaigns', () => ({
  useCampaigns: vi.fn(),
}))

function setAdmin(isAdmin: boolean) {
  useAuthStore.setState({
    currentUser: { id: 'u0', email: 'admin@example.com', isAdmin, forcePasswordChange: false },
  })
}

const mockedUseCampaigns = vi.mocked(useCampaigns)

function mockResult(partial: Partial<ReturnType<typeof useCampaigns>>) {
  mockedUseCampaigns.mockReturnValue(partial as ReturnType<typeof useCampaigns>)
}

function renderPage() {
  return render(
    <MemoryRouter>
      <CampaignListPage />
    </MemoryRouter>
  )
}

const campaign: CampaignResponse = {
  id: 'c1',
  name: 'Curse of Strahd',
  createdBy: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  role: 'gm',
}

describe('CampaignListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setAdmin(false)
  })

  it('shows a New campaign link for admins', () => {
    setAdmin(true)
    mockResult({ data: [campaign], isLoading: false, isError: false })

    renderPage()

    expect(screen.getByRole('link', { name: /new campaign/i })).toHaveAttribute(
      'href',
      '/campaigns/new'
    )
  })

  it('hides the New campaign link for non-admins', () => {
    setAdmin(false)
    mockResult({ data: [campaign], isLoading: false, isError: false })

    renderPage()

    expect(screen.queryByRole('link', { name: /new campaign/i })).not.toBeInTheDocument()
  })

  it('renders a card linking to each campaign with its role badge', () => {
    mockResult({ data: [campaign], isLoading: false, isError: false })

    renderPage()

    const link = screen.getByRole('link', { name: /curse of strahd/i })
    expect(link).toHaveAttribute('href', '/campaigns/c1')
    expect(screen.getByText('gm')).toBeInTheDocument()
  })

  it('shows the empty state when there are no campaigns', () => {
    mockResult({ data: [], isLoading: false, isError: false })

    renderPage()

    expect(
      screen.getByText(/no campaigns yet — ask your gm or an admin to add you/i)
    ).toBeInTheDocument()
  })

  it('shows an error banner when the list fails to load', () => {
    mockResult({ data: undefined, isLoading: false, isError: true })

    renderPage()

    expect(screen.getByText(/couldn't load your campaigns/i)).toBeInTheDocument()
  })

  it('shows skeleton placeholders while loading', () => {
    mockResult({ data: undefined, isLoading: true, isError: false })

    renderPage()

    expect(screen.queryByRole('link', { name: /curse of strahd/i })).not.toBeInTheDocument()
  })

  it('has no accessibility violations with a populated list', async () => {
    mockResult({ data: [campaign], isLoading: false, isError: false })

    const { container } = renderPage()

    expect(await axe(container)).toHaveNoViolations()
  })
})
