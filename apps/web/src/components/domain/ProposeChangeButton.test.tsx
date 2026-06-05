import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { ProposeChangeButton } from './ProposeChangeButton'

describe('ProposeChangeButton', () => {
  it('renders a button labelled "Propose a change"', () => {
    render(<ProposeChangeButton />)
    expect(screen.getByRole('button', { name: /propose a change/i })).toBeInTheDocument()
  })

  it('is disabled', () => {
    render(<ProposeChangeButton />)
    expect(screen.getByRole('button', { name: /propose a change/i })).toBeDisabled()
  })

  it('has aria-disabled="true"', () => {
    render(<ProposeChangeButton />)
    expect(screen.getByRole('button', { name: /propose a change/i })).toHaveAttribute(
      'aria-disabled',
      'true',
    )
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<ProposeChangeButton />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
