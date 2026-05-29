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

  it('shows the campaign name and the entry cards', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: /curse of strahd/i })).toBeInTheDocument()
    expect(screen.getByText('New session')).toBeInTheDocument()
    expect(screen.getByText('Query')).toBeInTheDocument()
    expect(screen.getByText('Exploration')).toBeInTheDocument()
  })

  it('links the New session card to the campaign sessions/new route', () => {
    renderPage()

    expect(screen.getByRole('link', { name: /new session/i })).toHaveAttribute(
      'href',
      '/campaigns/c1/sessions/new'
    )
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()

    expect(await axe(container)).toHaveNoViolations()
  })
})
