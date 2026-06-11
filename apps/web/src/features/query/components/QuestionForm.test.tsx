import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
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

  it('shows a character counter reflecting the trimmed question length', async () => {
    render(<QuestionForm onSubmit={vi.fn()} isPending={false} />)
    const textarea = screen.getByRole('textbox')

    await userEvent.type(textarea, '  Hello world  ')
    expect(screen.getByText('11/2000')).toBeInTheDocument()
  })

  it('disables submission when the trimmed question exceeds 2000 characters', () => {
    render(<QuestionForm onSubmit={vi.fn()} isPending={false} />)
    const textarea = screen.getByRole('textbox')

    fireEvent.change(textarea, { target: { value: 'a'.repeat(2001) } })

    expect(screen.getByRole('button', { name: /ask/i })).toBeDisabled()
  })

  it('shows amber counter colour when the question approaches the limit', () => {
    render(<QuestionForm onSubmit={vi.fn()} isPending={false} />)

    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'a'.repeat(1900) } })

    expect(screen.getByText('1900/2000')).toHaveClass('text-amber-600')
  })

  it('shows red counter colour when the question is at the limit', () => {
    render(<QuestionForm onSubmit={vi.fn()} isPending={false} />)

    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'a'.repeat(2000) } })

    expect(screen.getByText('2000/2000')).toHaveClass('text-red-600')
  })

  it('clears the textarea after the question is submitted', async () => {
    render(<QuestionForm onSubmit={vi.fn()} isPending={false} />)
    const textarea = screen.getByRole('textbox')

    await userEvent.type(textarea, 'Where is Aldric?')
    await userEvent.click(screen.getByRole('button', { name: /ask/i }))

    expect(textarea).toHaveValue('')
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<QuestionForm onSubmit={vi.fn()} isPending={false} />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
