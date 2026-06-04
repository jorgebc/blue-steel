import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { TimelineSkeleton } from './TimelineSkeleton'

describe('TimelineSkeleton', () => {
  it('exposes a labelled loading status', () => {
    render(<TimelineSkeleton />)
    expect(screen.getByRole('status', { name: /loading timeline/i })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<TimelineSkeleton />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
