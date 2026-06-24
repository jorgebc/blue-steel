import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { InlineBanner } from './InlineBanner'

describe('InlineBanner', () => {
  it('renders the message text', () => {
    render(<InlineBanner variant="info" message="Operation complete" onDismiss={vi.fn()} />)
    expect(screen.getByText('Operation complete')).toBeInTheDocument()
  })

  it('has role="alert" and aria-live="polite"', () => {
    render(<InlineBanner variant="info" message="test" onDismiss={vi.fn()} />)
    const el = screen.getByRole('alert')
    expect(el).toHaveAttribute('aria-live', 'polite')
  })

  it('calls onDismiss when the dismiss button is clicked', async () => {
    const onDismiss = vi.fn()
    render(<InlineBanner variant="success" message="Saved" onDismiss={onDismiss} />)
    await userEvent.click(screen.getByRole('button', { name: /dismiss/i }))
    expect(onDismiss).toHaveBeenCalledOnce()
  })

  describe('auto-clear behavior', () => {
    beforeEach(() => vi.useFakeTimers())
    afterEach(() => vi.useRealTimers())

    it.each(['success', 'warning', 'info'] as const)(
      '%s variant calls onDismiss after 8 seconds',
      (variant) => {
        const onDismiss = vi.fn()
        render(<InlineBanner variant={variant} message="msg" onDismiss={onDismiss} />)
        act(() => vi.advanceTimersByTime(8000))
        expect(onDismiss).toHaveBeenCalledOnce()
      }
    )

    it('error variant does not auto-clear after 8 seconds', () => {
      const onDismiss = vi.fn()
      render(<InlineBanner variant="error" message="Something went wrong" onDismiss={onDismiss} />)
      act(() => vi.advanceTimersByTime(8000))
      expect(onDismiss).not.toHaveBeenCalled()
    })
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <InlineBanner variant="info" message="Accessibility check" onDismiss={vi.fn()} />
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
