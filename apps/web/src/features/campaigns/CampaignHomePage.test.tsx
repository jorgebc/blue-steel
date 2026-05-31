import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { CampaignHomePage } from './CampaignHomePage'
import { useCampaign } from '@/api/campaigns'
import type { CampaignResponse } from '@/types/campaign'

vi.mock('@/api/campaigns', () => ({
  useCampaign: vi.fn(),
}))

vi.mock('./components/MemberManagementPanel', () => ({
  MemberManagementPanel: ({ campaignId }: { campaignId: string }) => (
    <div data-testid="member-panel">members for {campaignId}</div>
  ),
}))

const mockedUseCampaign = vi.mocked(useCampaign)

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
  })

  it('shows the campaign name as the page heading', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: /curse of strahd/i })).toBeInTheDocument()
  })

  it('shows a welcome message pointing to the sidebar', () => {
    renderPage()

    expect(screen.getByText(/use the sidebar/i)).toBeInTheDocument()
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

  it('has no accessibility violations', async () => {
    const { container } = renderPage()

    expect(await axe(container)).toHaveNoViolations()
  })
})
