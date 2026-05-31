import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { FocusedOverlay } from './FocusedOverlay'

describe('FocusedOverlay', () => {
  it('renders nothing when closed', () => {
    const { container } = render(
      <FocusedOverlay open={false} onClose={vi.fn()}>
        <p>Overlay body</p>
      </FocusedOverlay>
    )

    expect(screen.queryByText('Overlay body')).not.toBeInTheDocument()
    expect(container).toBeEmptyDOMElement()
  })

  it('renders the children inside a dialog when open', () => {
    render(
      <FocusedOverlay open onClose={vi.fn()}>
        <p>Overlay body</p>
      </FocusedOverlay>
    )

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByText('Overlay body')).toBeInTheDocument()
  })

  it('closes when the backdrop is clicked', async () => {
    const onClose = vi.fn()
    const { container } = render(
      <FocusedOverlay open onClose={onClose}>
        <p>Overlay body</p>
      </FocusedOverlay>
    )

    const backdrop = container.querySelector('[aria-hidden="true"]')
    expect(backdrop).not.toBeNull()
    await userEvent.click(backdrop as Element)

    expect(onClose).toHaveBeenCalledOnce()
  })

  it('closes when Escape is pressed', async () => {
    const onClose = vi.fn()
    render(
      <FocusedOverlay open onClose={onClose}>
        <p>Overlay body</p>
      </FocusedOverlay>
    )

    await userEvent.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalledOnce()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <FocusedOverlay open onClose={vi.fn()} ariaLabel="Edit entity">
        <button type="button">Action</button>
      </FocusedOverlay>
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
