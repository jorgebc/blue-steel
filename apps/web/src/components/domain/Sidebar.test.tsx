import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { Sidebar } from './Sidebar'
import { useAuthStore } from '@/store/authStore'
import { useCampaignStore } from '@/store/campaignStore'
import { useUiStore } from '@/store/uiStore'
import type { CampaignRole } from '@/types/campaign'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('@/api/campaigns', () => ({
  useCampaign: () => ({ data: { id: 'c1', name: 'Curse of Strahd', role: 'gm' } }),
}))

function setup(role: CampaignRole | null = 'gm', expanded = true) {
  useUiStore.setState({ sidebarExpanded: expanded })
  useCampaignStore.setState({ activeCampaignId: 'c1', activeRole: role })
  useAuthStore.setState({
    currentUser: { id: 'u1', email: 'gm@example.com', isAdmin: false, forcePasswordChange: false },
  })
  return render(
    <MemoryRouter>
      <Sidebar />
    </MemoryRouter>
  )
}

describe('Sidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders the active campaign name and a switch-campaign link', () => {
    setup()
    expect(screen.getByText('Curse of Strahd')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /switch campaign/i })).toHaveAttribute('href', '/')
  })

  it('shows the Input mode link pointing at the new-session route for gm', () => {
    setup('gm')
    expect(screen.getByRole('link', { name: /input/i })).toHaveAttribute(
      'href',
      '/campaigns/c1/sessions/new'
    )
  })

  it('shows the Input mode link for editor', () => {
    setup('editor')
    expect(screen.getByRole('link', { name: /input/i })).toBeInTheDocument()
  })

  it('hides the Input mode link entirely for player', () => {
    setup('player')
    expect(screen.queryByRole('link', { name: /input/i })).not.toBeInTheDocument()
  })

  it('renders Query, Exploration, and Settings as disabled coming-soon items', () => {
    setup('gm')
    for (const label of ['Query', 'Exploration', 'Settings']) {
      const item = screen.getByText(label).closest('[aria-disabled]')
      expect(item).toHaveAttribute('aria-disabled', 'true')
    }
  })

  it('toggles the sidebar when the collapse button is clicked', async () => {
    setup('gm', true)
    await userEvent.click(screen.getByRole('button', { name: /collapse sidebar/i }))
    expect(useUiStore.getState().sidebarExpanded).toBe(false)
  })

  it('hides text labels when collapsed', () => {
    setup('gm', false)
    expect(screen.queryByText('Curse of Strahd')).not.toBeInTheDocument()
    expect(screen.queryByText('Input')).not.toBeInTheDocument()
  })

  it('logs out and navigates to /login', async () => {
    setup('gm')
    await userEvent.click(screen.getByRole('button', { name: /log out/i }))
    expect(useAuthStore.getState().currentUser).toBeNull()
    expect(mockNavigate).toHaveBeenCalledWith('/login')
  })

  it('has no accessibility violations', async () => {
    const { container } = setup('gm')
    expect(await axe(container)).toHaveNoViolations()
  })
})
