import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { EntityListSkeleton } from './EntityListSkeleton'

describe('EntityListSkeleton', () => {
  it('exposes a labelled loading status', () => {
    render(<EntityListSkeleton />)

    expect(screen.getByRole('status', { name: /loading entities/i })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<EntityListSkeleton />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
