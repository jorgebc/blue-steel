import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { TimelinePage } from './TimelinePage'
import { useTimeline } from '@/api/timeline'
import type { TimelineEvent } from '@/types/timeline'

vi.mock('@/api/timeline', () => ({ useTimeline: vi.fn() }))
const mockUseTimeline = vi.mocked(useTimeline)

const event: TimelineEvent = {
  eventId: 'e1',
  name: 'Ambush at the Pass',
  eventType: 'battle',
  involvedActorNames: ['Aldric'],
  spaceName: 'Mountain Pass',
  sessionId: 's1',
  sessionSequenceNumber: 1,
  createdAt: '2026-01-01T09:00:00Z',
}

const fetchNextPage = vi.fn()

type TimelineResult = ReturnType<typeof useTimeline>

function mockResult(over: Partial<TimelineResult>) {
  mockUseTimeline.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage,
    ...over,
  } as unknown as TimelineResult)
}

function pageData(events: TimelineEvent[]) {
  return { pages: [{ events, nextCursor: null }], pageParams: [undefined] }
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/explore/timeline']}>
      <Routes>
        <Route path="/campaigns/:campaignId/explore/timeline" element={<TimelinePage />} />
      </Routes>
    </MemoryRouter>
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockResult({ data: pageData([event]) })
})

describe('TimelinePage', () => {
  it('renders an EventCard per feed entry', () => {
    renderPage()

    const link = screen.getByRole('link', { name: /Ambush at the Pass/i })
    expect(link).toHaveAttribute('href', '/campaigns/c1/explore/events/e1')
  })

  it('shows the loading skeleton while fetching the first page', () => {
    mockResult({ isLoading: true })
    renderPage()

    expect(screen.getByRole('status', { name: /loading timeline/i })).toBeInTheDocument()
  })

  it('shows an error banner when the request fails', () => {
    mockResult({ isError: true })
    renderPage()

    expect(screen.getByRole('alert')).toHaveTextContent(/could not load the timeline/i)
  })

  it('shows an empty state when there are no events', () => {
    mockResult({ data: pageData([]) })
    renderPage()

    expect(screen.getByText(/no events yet/i)).toBeInTheDocument()
  })

  it('disables Load more when there is no next page', () => {
    renderPage()

    expect(screen.getByRole('button', { name: /load more/i })).toBeDisabled()
  })

  it('fetches the next page when Load more is clicked', async () => {
    mockResult({ data: pageData([event]), hasNextPage: true })
    renderPage()

    await userEvent.click(screen.getByRole('button', { name: /load more/i }))
    expect(fetchNextPage).toHaveBeenCalledOnce()
  })

  it('applies the trimmed filters to the hook on submit', async () => {
    renderPage()

    await userEvent.type(screen.getByLabelText(/event type/i), 'battle')
    await userEvent.click(screen.getByRole('button', { name: /apply/i }))

    expect(mockUseTimeline).toHaveBeenLastCalledWith({ eventType: 'battle' })
  })

  it('groups events under a heading per session', () => {
    const laterEvent: TimelineEvent = {
      ...event,
      eventId: 'e2',
      name: 'Council at Rivendell',
      sessionId: 's2',
      sessionSequenceNumber: 2,
    }
    mockResult({ data: pageData([event, laterEvent]) })
    renderPage()

    expect(screen.getByRole('heading', { name: 'Session #1' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Session #2' })).toBeInTheDocument()
  })

  it('clears active filters when Clear is clicked', async () => {
    renderPage()

    await userEvent.type(screen.getByLabelText(/event type/i), 'battle')
    await userEvent.click(screen.getByRole('button', { name: /apply/i }))
    await userEvent.click(screen.getByRole('button', { name: /clear/i }))

    expect(mockUseTimeline).toHaveBeenLastCalledWith({})
    expect(screen.getByLabelText(/event type/i)).toHaveValue('')
  })

  it('distinguishes a filtered-empty feed from a truly empty one', async () => {
    mockResult({ data: pageData([]) })
    renderPage()

    expect(screen.getByText(/no events yet/i)).toBeInTheDocument()

    await userEvent.type(screen.getByLabelText(/event type/i), 'ghost')
    await userEvent.click(screen.getByRole('button', { name: /apply/i }))

    expect(screen.getByText(/no events match these filters/i)).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPage()
    expect(await axe(container)).toHaveNoViolations()
  })
})
