import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { QuestionForm } from './QuestionForm'

describe('QuestionForm', () => {
  it('associates a visually hidden label with the textarea', () => {
    render(<QuestionForm onSubmit={vi.fn()} isPending={false} />)
    expect(
      screen.getByRole('textbox', { name: /ask a question about the campaign/i })
    ).toBeInTheDocument()
  })

  it('submits the trimmed question text', async () => {
    const onSubmit = vi.fn()
    render(<QuestionForm onSubmit={onSubmit} isPending={false} />)

    await userEvent.type(screen.getByRole('textbox'), '  Where is Aldric?  ')
    await userEvent.click(screen.getByRole('button', { name: /ask/i }))

    expect(onSubmit).toHaveBeenCalledWith('Where is Aldric?')
  })

  it('disables submission and does not call onSubmit when the question is blank', async () => {
    const onSubmit = vi.fn()
    render(<QuestionForm onSubmit={onSubmit} isPending={false} />)

    const button = screen.getByRole('button', { name: /ask/i })
    expect(button).toBeDisabled()

    await userEvent.type(screen.getByRole('textbox'), '   ')
    expect(button).toBeDisabled()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('disables the input and button while a query is pending', () => {
    render(<QuestionForm onSubmit={vi.fn()} isPending={true} />)

    expect(screen.getByRole('textbox')).toBeDisabled()
    expect(screen.getByRole('button', { name: /searching/i })).toBeDisabled()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<QuestionForm onSubmit={vi.fn()} isPending={false} />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
