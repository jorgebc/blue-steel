import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { axe } from 'vitest-axe'
import { EventCard } from './EventCard'
import type { TimelineEvent } from '@/types/timeline'

const event: TimelineEvent = {
  eventId: 'e1',
  name: 'Ambush at the Pass',
  eventType: 'battle',
  involvedActorNames: ['Aldric', 'Seraphine'],
  spaceName: 'Mountain Pass',
  sessionId: 's1',
  sessionSequenceNumber: 2,
  createdAt: '2026-01-01T09:00:00Z',
}

function renderCard(over: Partial<TimelineEvent> = {}) {
  return render(
    <MemoryRouter initialEntries={['/campaigns/c1/explore/timeline']}>
      <Routes>
        <Route
          path="/campaigns/:campaignId/explore/timeline"
          element={<EventCard event={{ ...event, ...over }} />}
        />
      </Routes>
    </MemoryRouter>
  )
}

describe('EventCard', () => {
  it('links to the event detail route for the campaign', () => {
    renderCard()

    const link = screen.getByRole('link', { name: /Ambush at the Pass/i })
    expect(link).toHaveAttribute('href', '/campaigns/c1/explore/events/e1')
  })

  it('renders the event name, type badge, actors and space', () => {
    renderCard()

    expect(screen.getByText('Ambush at the Pass')).toBeInTheDocument()
    expect(screen.getByText('battle')).toBeInTheDocument()
    expect(screen.getByText(/Aldric, Seraphine/)).toBeInTheDocument()
    expect(screen.getByText(/Mountain Pass/)).toBeInTheDocument()
  })

  it('does not repeat the session reference (the timeline groups by session)', () => {
    renderCard()

    expect(screen.queryByText(/Session #/)).not.toBeInTheDocument()
  })

  it('omits the type badge when the event has no type', () => {
    renderCard({ eventType: null })

    expect(screen.queryByText('battle')).not.toBeInTheDocument()
    expect(screen.getByText('Ambush at the Pass')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderCard()
    expect(await axe(container)).toHaveNoViolations()
  })
})
