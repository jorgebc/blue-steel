import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { CampaignContextGuard } from './CampaignContextGuard'
import { apiClient } from '@/api/client'
import { useCampaignStore } from '@/store/campaignStore'
import { createTestQueryClient } from '@/test/createTestQueryClient'
import type { CampaignResponse } from '@/types/campaign'

vi.mock('@/api/client', () => ({
  apiClient: { get: vi.fn() },
}))

const campaign: CampaignResponse = {
  id: 'c1',
  name: 'Curse of Strahd',
  createdBy: 'u1',
  createdAt: '2026-01-01T00:00:00Z',
  contentLanguage: 'en',
  role: 'editor',
}

function envelope<T>(data: T) {
  return { data, meta: null, errors: [] }
}

function renderGuard() {
  const queryClient = createTestQueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/campaigns/c1']}>
        <Routes>
          <Route path="/campaigns/:campaignId" element={<CampaignContextGuard />}>
            <Route index element={<div>Campaign child</div>} />
          </Route>
          <Route path="/" element={<div>Campaign list</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('CampaignContextGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useCampaignStore.getState().clearCampaign()
  })

  it('shows a loading skeleton (no spinner, no child) while the campaign resolves', () => {
    vi.mocked(apiClient.get).mockReturnValue(new Promise(() => {}))

    renderGuard()

    expect(screen.queryByText('Campaign child')).not.toBeInTheDocument()
    expect(screen.queryByText(/couldn't load this campaign/i)).not.toBeInTheDocument()
  })

  it('sets the active campaign + role and renders the subtree on success', async () => {
    vi.mocked(apiClient.get).mockResolvedValue(envelope(campaign))

    renderGuard()

    await waitFor(() => expect(screen.getByText('Campaign child')).toBeInTheDocument())
    expect(useCampaignStore.getState().activeCampaignId).toBe('c1')
    expect(useCampaignStore.getState().activeRole).toBe('editor')
  })

  it('shows an inline error with a link home when the campaign fails to load', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error('forbidden'))

    renderGuard()

    await waitFor(() =>
      expect(screen.getByText(/couldn't load this campaign/i)).toBeInTheDocument()
    )
    expect(screen.getByRole('link', { name: /back to campaigns/i })).toBeInTheDocument()
    expect(screen.queryByText('Campaign child')).not.toBeInTheDocument()
  })

  it('has no accessibility violations on the error state', async () => {
    vi.mocked(apiClient.get).mockRejectedValue(new Error('forbidden'))

    const { container } = renderGuard()

    await waitFor(() =>
      expect(screen.getByText(/couldn't load this campaign/i)).toBeInTheDocument()
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
