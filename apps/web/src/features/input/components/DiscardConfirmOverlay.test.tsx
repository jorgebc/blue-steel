import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { DiscardConfirmOverlay } from './DiscardConfirmOverlay'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('DiscardConfirmOverlay', () => {
  it('renders nothing when closed', () => {
    render(
      <DiscardConfirmOverlay open={false} onConfirm={vi.fn()} onClose={vi.fn()} isPending={false} />
    )

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('calls onConfirm when the discard button is clicked', async () => {
    const onConfirm = vi.fn()
    render(<DiscardConfirmOverlay open onConfirm={onConfirm} onClose={vi.fn()} isPending={false} />)

    await userEvent.click(screen.getByRole('button', { name: /discard draft/i }))

    expect(onConfirm).toHaveBeenCalledOnce()
  })

  it('calls onClose when Cancel is clicked', async () => {
    const onClose = vi.fn()
    render(<DiscardConfirmOverlay open onConfirm={vi.fn()} onClose={onClose} isPending={false} />)

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onClose).toHaveBeenCalledOnce()
  })

  it('disables both buttons while pending', () => {
    render(<DiscardConfirmOverlay open onConfirm={vi.fn()} onClose={vi.fn()} isPending />)

    expect(screen.getByRole('button', { name: /discard draft/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <DiscardConfirmOverlay open onConfirm={vi.fn()} onClose={vi.fn()} isPending={false} />
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
