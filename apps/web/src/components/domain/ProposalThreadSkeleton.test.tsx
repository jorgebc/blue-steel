import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { ProposalThreadSkeleton } from './ProposalThreadSkeleton'

describe('ProposalThreadSkeleton', () => {
  it('exposes an accessible loading status', () => {
    render(<ProposalThreadSkeleton />)
    expect(screen.getByRole('status', { name: /loading proposals/i })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<ProposalThreadSkeleton />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
