import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { DeltaFieldsEditor } from './DeltaFieldsEditor'

describe('DeltaFieldsEditor', () => {
  it('renders an editable input for each primitive field, seeded from values', () => {
    render(
      <DeltaFieldsEditor
        baseline={{ name: 'Ari', description: 'thief' }}
        values={{ name: 'Ari', description: 'thief' }}
        onChange={vi.fn()}
        idPrefix="t"
      />
    )
    expect(screen.getByLabelText('name')).toHaveValue('Ari')
    expect(screen.getByLabelText('description')).toHaveValue('thief')
  })

  it('shows structured fields read-only and marks them not editable', () => {
    render(
      <DeltaFieldsEditor
        baseline={{ aliases: ['Shadow'] }}
        values={{}}
        onChange={vi.fn()}
        idPrefix="t"
      />
    )
    expect(screen.queryByLabelText('aliases')).not.toBeInTheDocument()
    expect(screen.getByText(/not editable/i)).toBeInTheDocument()
  })

  it('reports edits through onChange', async () => {
    const onChange = vi.fn()
    render(
      <DeltaFieldsEditor
        baseline={{ name: 'Ari' }}
        values={{ name: 'Ari' }}
        onChange={onChange}
        idPrefix="t"
      />
    )
    await userEvent.type(screen.getByLabelText('name'), '!')
    expect(onChange).toHaveBeenLastCalledWith('name', 'Ari!')
  })

  it('shows an empty-state message when there are no fields', () => {
    render(<DeltaFieldsEditor baseline={{}} values={{}} onChange={vi.fn()} idPrefix="t" />)
    expect(screen.getByText(/no recorded fields/i)).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <DeltaFieldsEditor
        baseline={{ name: 'Ari' }}
        values={{ name: 'Ari' }}
        onChange={vi.fn()}
        idPrefix="t"
      />
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
