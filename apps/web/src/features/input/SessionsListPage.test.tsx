import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { SessionsListPage } from './SessionsListPage'
import { useSessions, useDiscardSession } from '@/api/sessions'
import { useCampaignStore } from '@/store/campaignStore'
import type { SessionSummary } from '@/types/session'
import type { UseMutationResult, UseQueryResult } from '@tanstack/react-query'

vi.mock('@/api/sessions', () => ({
  useSessions: vi.fn(),
  useDiscardSession: vi.fn(),
}))

vi.mock('@/store/campaignStore', () => ({
  useCampaignStore: vi.fn(),
}))

vi.mock('./components/DiscardConfirmOverlay', () => ({
  DiscardConfirmOverlay: ({
    open,
    onConfirm,
    onClose,
  }: {
    open: boolean
    onConfirm: () => void
    onClose: () => void
    isPending: boolean
  }) =>
    open ? (
      <div data-testid="discard-overlay">
        <button onClick={onConfirm}>Confirm discard</button>
        <button onClick={onClose}>Cancel</button>
      </div>
    ) : null,
}))

const mockUseSessions = vi.mocked(useSessions)
const mockUseDiscardSession = vi.mocked(useDiscardSession)
const mockUseCampaignStore = vi.mocked(useCampaignStore)

const committed: SessionSummary = {
  sessionId: 's1',
  status: 'COMMITTED',
  sequenceNumber: 1,
  committedAt: '2026-01-01T10:00:00Z',
  createdAt: '2026-01-01T09:00:00Z',
}

const draft: SessionSummary = {
  sessionId: 's2',
  status: 'DRAFT',
  sequenceNumber: 2,
  committedAt: null,
  createdAt: '2026-01-02T09:00:00Z',
}

const failed: SessionSummary = {
  sessionId: 's3',
  status: 'FAILED',
  sequenceNumber: 3,
  committedAt: null,
  createdAt: '2026-01-03T09:00:00Z',
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/sessions']}>
      <Routes>
        <Route path="/campaigns/:campaignId/sessions" element={<SessionsListPage />} />
        <Route
          path="/campaigns/:campaignId/sessions/:sessionId/diff"
          element={<div>Diff page</div>}
        />
      </Routes>
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockUseCampaignStore.mockReturnValue('gm')
  mockUseDiscardSession.mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
  } as unknown as UseMutationResult<void, Error, void>)
})

describe('SessionsListPage', () => {
  describe('loading state', () => {
    it('renders a skeleton list while sessions are loading', () => {
      mockUseSessions.mockReturnValue({
        isLoading: true,
        isError: false,
        data: undefined,
      } as unknown as UseQueryResult<SessionSummary[]>)

      renderPage()

      expect(screen.getByRole('status', { name: /loading sessions/i })).toBeInTheDocument()
    })
  })

  describe('error state', () => {
    it('shows an error banner when the sessions request fails', () => {
      mockUseSessions.mockReturnValue({
        isLoading: false,
        isError: true,
        data: undefined,
      } as unknown as UseQueryResult<SessionSummary[]>)

      renderPage()

      expect(screen.getByRole('alert')).toBeInTheDocument()
      expect(screen.getByText(/could not load sessions/i)).toBeInTheDocument()
    })
  })

  describe('empty state', () => {
    it('shows an empty message when there are no sessions', () => {
      mockUseSessions.mockReturnValue({
        isLoading: false,
        isError: false,
        data: [],
      } as unknown as UseQueryResult<SessionSummary[]>)

      renderPage()

      expect(screen.getByText(/no sessions yet/i)).toBeInTheDocument()
    })
  })

  describe('session list', () => {
    beforeEach(() => {
      mockUseSessions.mockReturnValue({
        isLoading: false,
        isError: false,
        data: [committed, draft, failed],
      } as unknown as UseQueryResult<SessionSummary[]>)
    })

    it('renders the page heading', () => {
      renderPage()

      expect(screen.getByRole('heading', { name: /session history/i })).toBeInTheDocument()
    })

    it('renders a status badge for each session', () => {
      renderPage()

      expect(screen.getByText('Committed')).toBeInTheDocument()
      expect(screen.getByText('Draft')).toBeInTheDocument()
      expect(screen.getByText('Failed')).toBeInTheDocument()
    })

    it('renders a sequence number for each session', () => {
      renderPage()

      expect(screen.getByText('#1')).toBeInTheDocument()
      expect(screen.getByText('#2')).toBeInTheDocument()
      expect(screen.getByText('#3')).toBeInTheDocument()
    })

    it('links each row content to the session detail page', () => {
      const { container } = renderPage()

      expect(container.querySelector('a[href="/campaigns/c1/sessions/s1"]')).toBeInTheDocument()
      expect(container.querySelector('a[href="/campaigns/c1/sessions/s2"]')).toBeInTheDocument()
      expect(container.querySelector('a[href="/campaigns/c1/sessions/s3"]')).toBeInTheDocument()
    })

    it('shows a Resume link for the draft session pointing to the diff page', () => {
      renderPage()

      const resumeLink = screen.getByRole('link', { name: /resume/i })
      expect(resumeLink).toHaveAttribute('href', '/campaigns/c1/sessions/s2/diff')
    })

    it('does not show a Resume link for committed or failed sessions', () => {
      renderPage()

      expect(screen.getAllByRole('link', { name: /resume/i })).toHaveLength(1)
    })

    it('shows a Discard button for the draft session when the user is a GM', () => {
      renderPage()

      expect(screen.getByRole('button', { name: /discard/i })).toBeInTheDocument()
    })

    it('does not show a Discard button for committed or failed sessions', () => {
      renderPage()

      expect(screen.getAllByRole('button', { name: /discard/i })).toHaveLength(1)
    })
  })

  describe('discard flow', () => {
    beforeEach(() => {
      mockUseSessions.mockReturnValue({
        isLoading: false,
        isError: false,
        data: [draft],
      } as unknown as UseQueryResult<SessionSummary[]>)
    })

    it('opens the discard confirmation overlay when Discard is clicked', async () => {
      renderPage()

      await userEvent.click(screen.getByRole('button', { name: /discard/i }))

      expect(screen.getByTestId('discard-overlay')).toBeInTheDocument()
    })

    it('calls useDiscardSession mutate when the user confirms the discard', async () => {
      const mockMutate = vi.fn()
      mockUseDiscardSession.mockReturnValue({
        mutate: mockMutate,
        isPending: false,
      } as unknown as UseMutationResult<void, Error, void>)

      renderPage()

      await userEvent.click(screen.getByRole('button', { name: /discard/i }))
      await userEvent.click(screen.getByRole('button', { name: /confirm discard/i }))

      expect(mockMutate).toHaveBeenCalled()
    })

    it('closes the overlay when Cancel is clicked', async () => {
      renderPage()

      await userEvent.click(screen.getByRole('button', { name: /discard/i }))
      await userEvent.click(screen.getByRole('button', { name: /cancel/i }))

      expect(screen.queryByTestId('discard-overlay')).not.toBeInTheDocument()
    })

    it('shows an error banner when the discard mutation fails', async () => {
      const mockMutate = vi.fn((_: unknown, opts: { onError?: () => void }) => {
        opts?.onError?.()
      })
      mockUseDiscardSession.mockReturnValue({
        mutate: mockMutate,
        isPending: false,
      } as unknown as UseMutationResult<void, Error, void>)

      renderPage()

      await userEvent.click(screen.getByRole('button', { name: /discard/i }))
      await userEvent.click(screen.getByRole('button', { name: /confirm discard/i }))

      await waitFor(() => {
        expect(screen.getByText(/failed to discard the draft/i)).toBeInTheDocument()
      })
    })
  })

  describe('role gating', () => {
    beforeEach(() => {
      mockUseSessions.mockReturnValue({
        isLoading: false,
        isError: false,
        data: [draft],
      } as unknown as UseQueryResult<SessionSummary[]>)
    })

    it('does not show the Resume link when the user is a player', () => {
      mockUseCampaignStore.mockReturnValue('player')

      renderPage()

      expect(screen.queryByRole('link', { name: /resume/i })).not.toBeInTheDocument()
    })

    it('does not show the Discard button when the user is a player', () => {
      mockUseCampaignStore.mockReturnValue('player')

      renderPage()

      expect(screen.queryByRole('button', { name: /discard/i })).not.toBeInTheDocument()
    })

    it('shows Resume and Discard to an editor', () => {
      mockUseCampaignStore.mockReturnValue('editor')

      renderPage()

      expect(screen.getByRole('link', { name: /resume/i })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: /discard/i })).toBeInTheDocument()
    })
  })

  describe('accessibility', () => {
    it('has no accessibility violations', async () => {
      mockUseSessions.mockReturnValue({
        isLoading: false,
        isError: false,
        data: [committed, draft, failed],
      } as unknown as UseQueryResult<SessionSummary[]>)

      const { container } = renderPage()

      expect(await axe(container)).toHaveNoViolations()
    })
  })
})
