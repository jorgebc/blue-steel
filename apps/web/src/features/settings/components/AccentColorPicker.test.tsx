import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { AccentColorPicker } from './AccentColorPicker'
import { ACCENT_PALETTE } from '../accentPalette'

describe('AccentColorPicker', () => {
  it('renders a swatch for every palette color, labelled by name', () => {
    render(<AccentColorPicker value={ACCENT_PALETTE[0].hex} onChange={vi.fn()} />)
    for (const color of ACCENT_PALETTE) {
      expect(screen.getByRole('radio', { name: color.name })).toBeInTheDocument()
    }
  })

  it('marks the swatch matching the current value as checked', () => {
    render(<AccentColorPicker value={ACCENT_PALETTE[2].hex} onChange={vi.fn()} />)
    expect(screen.getByRole('radio', { name: ACCENT_PALETTE[2].name })).toBeChecked()
    expect(screen.getByRole('radio', { name: ACCENT_PALETTE[0].name })).not.toBeChecked()
  })

  it('calls onChange with the hex of a clicked swatch', async () => {
    const onChange = vi.fn()
    render(<AccentColorPicker value={ACCENT_PALETTE[0].hex} onChange={onChange} />)
    await userEvent.click(screen.getByRole('radio', { name: ACCENT_PALETTE[3].name }))
    expect(onChange).toHaveBeenCalledWith(ACCENT_PALETTE[3].hex)
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <AccentColorPicker value={ACCENT_PALETTE[0].hex} onChange={vi.fn()} />
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
