import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { Sidebar } from './Sidebar'
import { useCampaignStore } from '@/store/campaignStore'
import { useUiStore } from '@/store/uiStore'
import type { CampaignRole } from '@/types/campaign'

vi.mock('@/api/campaigns', () => ({
  useCampaign: () => ({ data: { id: 'c1', name: 'Curse of Strahd', role: 'gm' } }),
}))

function setup(role: CampaignRole | null = 'gm', expanded = true) {
  useUiStore.setState({ sidebarExpanded: expanded })
  useCampaignStore.setState({ activeCampaignId: 'c1', activeRole: role })
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

  it('shows GM badge for gm role', () => {
    setup('gm')
    expect(screen.getByText('GM')).toBeInTheDocument()
  })

  it('shows Editor badge for editor role', () => {
    setup('editor')
    expect(screen.getByText('Editor')).toBeInTheDocument()
  })

  it('shows Player badge for player role', () => {
    setup('player')
    expect(screen.getByText('Player')).toBeInTheDocument()
  })

  it('hides role badge when activeRole is null', () => {
    setup(null)
    expect(screen.queryByText('GM')).not.toBeInTheDocument()
    expect(screen.queryByText('Editor')).not.toBeInTheDocument()
    expect(screen.queryByText('Player')).not.toBeInTheDocument()
  })

  it('shows the Home link pointing at the campaign home route for all roles', () => {
    for (const role of ['gm', 'editor', 'player'] as const) {
      const { unmount } = setup(role)
      expect(screen.getByRole('link', { name: /home/i })).toHaveAttribute('href', '/campaigns/c1')
      unmount()
    }
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

  it('shows the Query link pointing at the query route for all roles', () => {
    for (const role of ['gm', 'editor', 'player'] as const) {
      const { unmount } = setup(role)
      expect(screen.getByRole('link', { name: /query/i })).toHaveAttribute(
        'href',
        '/campaigns/c1/query'
      )
      unmount()
    }
  })

  it('renders Settings as a disabled coming-soon item', () => {
    setup('gm')
    const item = screen.getByText('Settings').closest('[aria-disabled]')
    expect(item).toHaveAttribute('aria-disabled', 'true')
  })

  it('shows the Exploration link pointing at the explore route for all roles', () => {
    for (const role of ['gm', 'editor', 'player'] as const) {
      const { unmount } = setup(role)
      expect(screen.getByRole('link', { name: /exploration/i })).toHaveAttribute(
        'href',
        '/campaigns/c1/explore'
      )
      unmount()
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
    expect(screen.queryByText('Home')).not.toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = setup('gm')
    expect(await axe(container)).toHaveNoViolations()
  })
})
