import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { CampaignHomePage } from './CampaignHomePage'
import { useCampaign, useDeleteCampaign } from '@/api/campaigns'
import { useAuthStore } from '@/store/authStore'
import { useCampaignStore } from '@/store/campaignStore'
import type { CampaignResponse } from '@/types/campaign'
import type { UseMutationResult } from '@tanstack/react-query'

vi.mock('@/api/campaigns', () => ({
  useCampaign: vi.fn(),
  useDeleteCampaign: vi.fn(),
}))

vi.mock('@/store/authStore', () => ({
  useAuthStore: vi.fn(),
}))

vi.mock('@/store/campaignStore', () => ({
  useCampaignStore: vi.fn(),
}))

vi.mock('./components/MemberManagementPanel', () => ({
  MemberManagementPanel: ({ campaignId }: { campaignId: string }) => (
    <div data-testid="member-panel">members for {campaignId}</div>
  ),
}))

vi.mock('./components/DeleteCampaignConfirmOverlay', () => ({
  DeleteCampaignConfirmOverlay: ({ open }: { open: boolean }) =>
    open ? <div data-testid="delete-overlay" /> : null,
}))

const mockedUseCampaign = vi.mocked(useCampaign)
const mockedUseDeleteCampaign = vi.mocked(useDeleteCampaign)
const mockedUseAuthStore = vi.mocked(useAuthStore)
const mockedUseCampaignStore = vi.mocked(useCampaignStore)

const campaign: CampaignResponse = {
  id: 'c1',
  name: 'Curse of Strahd',
  createdBy: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  role: 'gm',
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1']}>
      <Routes>
        <Route path="/campaigns/:campaignId" element={<CampaignHomePage />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('CampaignHomePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedUseCampaign.mockReturnValue({
      data: campaign,
    } as ReturnType<typeof useCampaign>)
    mockedUseDeleteCampaign.mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
    } as unknown as UseMutationResult<void, Error, string>)
    mockedUseAuthStore.mockReturnValue(false)
    mockedUseCampaignStore.mockReturnValue(vi.fn())
  })

  it('shows the campaign name as the page heading', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: /curse of strahd/i })).toBeInTheDocument()
  })

  it('shows a welcome message pointing to the sidebar', () => {
    renderPage()

    expect(screen.getByText(/use the sidebar/i)).toBeInTheDocument()
  })

  it('renders a Session history link pointing to the campaign sessions page', () => {
    renderPage()

    const link = screen.getByRole('link', { name: /session history/i })
    expect(link).toHaveAttribute('href', '/campaigns/c1/sessions')
  })

  it('shows the member management panel to a GM', () => {
    renderPage()

    expect(screen.getByTestId('member-panel')).toBeInTheDocument()
  })

  it('hides the member management panel from a non-GM member', () => {
    mockedUseCampaign.mockReturnValue({
      data: { ...campaign, role: 'player' },
    } as ReturnType<typeof useCampaign>)

    renderPage()

    expect(screen.queryByTestId('member-panel')).not.toBeInTheDocument()
  })

  it('shows the danger zone section for admins', () => {
    mockedUseAuthStore.mockReturnValue(true)

    renderPage()

    expect(screen.getByText('Danger zone')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /delete campaign/i })).toBeInTheDocument()
  })

  it('hides the danger zone section for non-admins', () => {
    mockedUseAuthStore.mockReturnValue(false)

    renderPage()

    expect(screen.queryByText('Danger zone')).not.toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()

    expect(await axe(container)).toHaveNoViolations()
  })
})
