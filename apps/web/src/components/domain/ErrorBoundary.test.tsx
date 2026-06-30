import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { axe } from 'vitest-axe'
import { ErrorBoundary } from './ErrorBoundary'

function Boom(): never {
  throw new Error('https://internal-api.example/secret leaked stack detail')
}

describe('ErrorBoundary', () => {
  // The thrown error logs to console.error via componentDidCatch — silence it.
  beforeEach(() => vi.spyOn(console, 'error').mockImplementation(() => {}))
  afterEach(() => vi.restoreAllMocks())

  it('renders children when no error is thrown', () => {
    render(
      <ErrorBoundary>
        <p>healthy content</p>
      </ErrorBoundary>
    )
    expect(screen.getByText('healthy content')).toBeInTheDocument()
  })

  it('renders the fallback when a child throws', () => {
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>
    )
    expect(screen.getByText('Something went wrong')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /reload/i })).toBeInTheDocument()
  })

  it('does not leak the caught error message or stack into the DOM', () => {
    const { container } = render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>
    )
    expect(container.textContent).not.toContain('internal-api.example')
    expect(container.textContent).not.toContain('leaked stack detail')
  })

  it('has no accessibility violations in the fallback state', async () => {
    const { container } = render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
