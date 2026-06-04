import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { RelationsGraphSkeleton } from './RelationsGraphSkeleton'

describe('RelationsGraphSkeleton', () => {
  it('exposes a labelled loading status', () => {
    render(<RelationsGraphSkeleton />)

    expect(screen.getByRole('status', { name: /loading relations/i })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<RelationsGraphSkeleton />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
