import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { QueryUsageNotice } from './QueryUsageNotice'
import type { QueryUsage } from '@/types/query'

const usage: QueryUsage = {
  consumedUsd: 0.25,
  capUsd: 1.0,
  requestsRemaining: 8,
  maxRequests: 10,
  windowSeconds: 60,
}

describe('QueryUsageNotice', () => {
  it('always renders the shared free-tier moderation message', () => {
    render(<QueryUsageNotice />)
    expect(screen.getByRole('note')).toHaveTextContent(/free ai tier/i)
    expect(screen.getByRole('note')).toHaveTextContent(/moderation/i)
  })

  it('renders the budget percentage when usage is available', () => {
    render(<QueryUsageNotice usage={usage} />)
    expect(screen.getByText(/shared daily budget: 25% used/i)).toBeInTheDocument()
  })

  it('omits the live figures until usage has loaded', () => {
    render(<QueryUsageNotice />)
    expect(screen.queryByText(/shared daily budget/i)).not.toBeInTheDocument()
  })

  it('hides the per-minute hint while requests remaining is comfortable', () => {
    render(<QueryUsageNotice usage={usage} />)
    expect(screen.queryByText(/questions left this minute/i)).not.toBeInTheDocument()
  })

  it('shows the per-minute hint when remaining requests run low', () => {
    render(<QueryUsageNotice usage={{ ...usage, requestsRemaining: 2 }} />)
    expect(screen.getByText(/2 questions left this minute/i)).toBeInTheDocument()
  })

  it('caps the budget bar at 100% when spend exceeds the cap', () => {
    render(<QueryUsageNotice usage={{ ...usage, consumedUsd: 1.5 }} />)
    expect(screen.getByText(/100% used/i)).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<QueryUsageNotice usage={usage} />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
