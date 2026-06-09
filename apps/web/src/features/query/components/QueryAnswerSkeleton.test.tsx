import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { QueryAnswerSkeleton } from './QueryAnswerSkeleton'

describe('QueryAnswerSkeleton', () => {
  it('exposes a labelled loading status for screen readers', () => {
    render(<QueryAnswerSkeleton />)
    expect(screen.getByRole('status', { name: /searching the world state/i })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<QueryAnswerSkeleton />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
