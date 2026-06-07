import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import type { UseQueryResult } from '@tanstack/react-query'
import { SessionDetailPage } from './SessionDetailPage'
import { useSessionDetail } from '@/api/sessions'
import type { SessionDetail } from '@/types/session'

vi.mock('@/api/sessions', () => ({ useSessionDetail: vi.fn() }))
const mockUseSessionDetail = vi.mocked(useSessionDetail)

const detail: SessionDetail = {
  sessionId: 's1',
  campaignId: 'c1',
  status: 'COMMITTED',
  sequenceNumber: 4,
  failureReason: null,
  committedAt: '2026-01-02T09:00:00Z',
  createdAt: '2026-01-01T09:00:00Z',
  updatedAt: '2026-01-02T09:00:00Z',
  narrativeBlockId: 'b1',
  narrativeSummary: 'The party stormed the keep and freed the prisoners.',
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/sessions/s1']}>
      <Routes>
        <Route path="/campaigns/:campaignId/sessions/:sessionId" element={<SessionDetailPage />} />
      </Routes>
    </MemoryRouter>
  )
}

function mockResult(over: Partial<UseQueryResult<SessionDetail>>) {
  mockUseSessionDetail.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    ...over,
  } as unknown as UseQueryResult<SessionDetail>)
}

beforeEach(() => {
  vi.clearAllMocks()
  mockResult({ data: detail })
})

describe('SessionDetailPage', () => {
  it('requests the session detail for the route ids', () => {
    renderPage()
    expect(mockUseSessionDetail).toHaveBeenCalledWith('c1', 's1')
  })

  it('renders the sequence number, status and narrative summary', () => {
    renderPage()

    expect(screen.getByRole('heading', { name: 'Session #4' })).toBeInTheDocument()
    expect(screen.getByText('Committed')).toBeInTheDocument()
    expect(
      screen.getByText('The party stormed the keep and freed the prisoners.')
    ).toBeInTheDocument()
  })

  it('renders a back link to the session list', () => {
    renderPage()

    expect(screen.getByRole('link', { name: /back to sessions/i })).toHaveAttribute(
      'href',
      '/campaigns/c1/sessions'
    )
  })

  it('shows a fallback when no narrative summary was recorded', () => {
    mockResult({ data: { ...detail, narrativeSummary: null } })
    renderPage()

    expect(screen.getByText('No narrative summary recorded.')).toBeInTheDocument()
  })

  it('shows the loading skeleton while fetching', () => {
    mockResult({ isLoading: true })
    renderPage()

    expect(screen.getByRole('status', { name: /loading session/i })).toBeInTheDocument()
  })

  it('shows an error banner when the request fails', () => {
    mockResult({ isError: true })
    renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent(/could not load this session/i)
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
