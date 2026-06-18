import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { QueryHistorySkeleton } from './QueryHistorySkeleton'

describe('QueryHistorySkeleton', () => {
  it('exposes a labelled loading status for screen readers', () => {
    render(<QueryHistorySkeleton />)
    expect(screen.getByRole('status', { name: /loading question history/i })).toBeInTheDocument()
  })

  it('renders animated placeholder rows', () => {
    const { container } = render(<QueryHistorySkeleton />)
    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0)
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<QueryHistorySkeleton />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
