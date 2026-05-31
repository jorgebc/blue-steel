import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { DiffCategorySection } from './DiffCategorySection'

describe('DiffCategorySection', () => {
  it('renders the title, count, and children expanded by default', () => {
    render(
      <DiffCategorySection title="Actors" count={2}>
        <p>card content</p>
      </DiffCategorySection>
    )

    expect(screen.getByRole('button', { name: /actors/i })).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('(2)')).toBeInTheDocument()
    expect(screen.getByText('card content')).toBeInTheDocument()
  })

  it('collapses and expands the children when the heading is toggled', async () => {
    render(
      <DiffCategorySection title="Spaces" count={1}>
        <p>card content</p>
      </DiffCategorySection>
    )

    const toggle = screen.getByRole('button', { name: /spaces/i })
    await userEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('card content')).not.toBeInTheDocument()

    await userEvent.click(toggle)
    expect(screen.getByText('card content')).toBeInTheDocument()
  })

  it('exposes the section with an accessible name', () => {
    render(
      <DiffCategorySection title="Events" count={0}>
        <p>none</p>
      </DiffCategorySection>
    )

    expect(screen.getByRole('region', { name: /events/i })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <DiffCategorySection title="Relations" count={3}>
        <p>card content</p>
      </DiffCategorySection>
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
