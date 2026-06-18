import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { QueryHistoryPanel } from './QueryHistoryPanel'
import { useQueryHistory } from '@/api/queries'
import type { QueryHistoryPage } from '@/types/query'

vi.mock('@/api/queries', async () => {
  const actual = await vi.importActual<typeof import('@/api/queries')>('@/api/queries')
  return { ...actual, useQueryHistory: vi.fn() }
})
const mockUseQueryHistory = vi.mocked(useQueryHistory)

const page: QueryHistoryPage = {
  items: [
    {
      id: 'q1',
      question: 'Where did Aldric go?',
      answer: 'Aldric fled north after the battle.',
      citations: [
        { sessionId: 's1', sequenceNumber: 3, snippet: 'Aldric was last seen heading north.' },
      ],
      createdAt: '2026-06-18T10:00:00Z',
    },
    {
      id: 'q2',
      question: 'Who rules the city?',
      answer: 'Queen Mara rules from the high keep.',
      citations: [],
      createdAt: '2026-06-17T09:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalCount: 2,
}

type HistoryResult = ReturnType<typeof useQueryHistory>

function stub(value: Partial<HistoryResult>) {
  mockUseQueryHistory.mockReturnValue(value as HistoryResult)
}

function renderPanel() {
  return render(
    <MemoryRouter>
      <QueryHistoryPanel campaignId="c1" />
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  stub({ data: page, isLoading: false, isError: false })
})

describe('QueryHistoryPanel', () => {
  it('shows the loading skeleton while the page is in flight', () => {
    stub({ data: undefined, isLoading: true, isError: false })
    renderPanel()
    expect(screen.getByRole('status', { name: /loading question history/i })).toBeInTheDocument()
  })

  it('shows an inline error banner when the history fails to load', () => {
    stub({ data: undefined, isLoading: false, isError: true })
    renderPanel()
    expect(screen.getByRole('alert')).toHaveTextContent(/could not load the question history/i)
  })

  it('shows an empty state when no questions have been asked', () => {
    stub({
      data: { items: [], page: 0, size: 20, totalCount: 0 },
      isLoading: false,
      isError: false,
    })
    renderPanel()
    expect(screen.getByText(/no questions have been asked yet/i)).toBeInTheDocument()
  })

  it('lists past questions newest-first', () => {
    renderPanel()
    expect(screen.getByRole('button', { name: /where did aldric go/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /who rules the city/i })).toBeInTheDocument()
  })

  it('reveals the selected entry answer and its citation link', async () => {
    renderPanel()
    await userEvent.click(screen.getByRole('button', { name: /where did aldric go/i }))

    expect(screen.getByText('Aldric fled north after the battle.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /session 3/i })).toHaveAttribute(
      'href',
      '/campaigns/c1/sessions/s1'
    )
  })

  it('does not render pagination controls for a single page', () => {
    renderPanel()
    expect(screen.queryByRole('navigation', { name: /history pages/i })).not.toBeInTheDocument()
  })

  it('enables Next when more pages exist and advances the page', async () => {
    stub({ data: { ...page, totalCount: 40 }, isLoading: false, isError: false })
    renderPanel()

    await userEvent.click(screen.getByRole('button', { name: /next/i }))

    expect(mockUseQueryHistory).toHaveBeenLastCalledWith('c1', 1)
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPanel()
    expect(await axe(container)).toHaveNoViolations()
  })
})
