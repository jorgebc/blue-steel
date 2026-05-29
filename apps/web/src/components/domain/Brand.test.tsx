import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { Brand } from './Brand'

describe('Brand', () => {
  it('renders the Blue Steel wordmark', () => {
    render(<Brand />)
    expect(screen.getByText('Blue Steel')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<Brand size="lg" />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
