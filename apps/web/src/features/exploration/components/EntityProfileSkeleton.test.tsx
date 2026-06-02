import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { EntityProfileSkeleton } from './EntityProfileSkeleton'

describe('EntityProfileSkeleton', () => {
  it('exposes a labelled loading status', () => {
    render(<EntityProfileSkeleton />)

    expect(screen.getByRole('status', { name: /loading profile/i })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<EntityProfileSkeleton />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
