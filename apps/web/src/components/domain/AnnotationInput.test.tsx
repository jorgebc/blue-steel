import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { AnnotationInput } from './AnnotationInput'

describe('AnnotationInput', () => {
  it('disables submit while the field is empty', () => {
    render(<AnnotationInput onSubmit={vi.fn()} isPending={false} />)
    expect(screen.getByRole('button', { name: /post annotation/i })).toBeDisabled()
  })

  it('enables submit once non-blank text is entered', async () => {
    render(<AnnotationInput onSubmit={vi.fn()} isPending={false} />)
    await userEvent.type(screen.getByRole('textbox', { name: /add an annotation/i }), 'Hello')
    expect(screen.getByRole('button', { name: /post annotation/i })).toBeEnabled()
  })

  it('submits the trimmed content and clears the field', async () => {
    const onSubmit = vi.fn()
    render(<AnnotationInput onSubmit={onSubmit} isPending={false} />)
    const field = screen.getByRole('textbox', { name: /add an annotation/i })

    await userEvent.type(field, '  a note  ')
    await userEvent.click(screen.getByRole('button', { name: /post annotation/i }))

    expect(onSubmit).toHaveBeenCalledWith('a note')
    expect(field).toHaveValue('')
  })

  it('disables the field and submit while pending', () => {
    render(<AnnotationInput onSubmit={vi.fn()} isPending />)
    expect(screen.getByRole('textbox', { name: /add an annotation/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /post annotation/i })).toBeDisabled()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<AnnotationInput onSubmit={vi.fn()} isPending={false} />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
