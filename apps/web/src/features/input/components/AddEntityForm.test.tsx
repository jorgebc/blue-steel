import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { AddEntityForm } from './AddEntityForm'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('AddEntityForm', () => {
  it('renders the type, name, and field controls', () => {
    render(<AddEntityForm onAdd={vi.fn()} onCancel={vi.fn()} />)

    expect(screen.getByLabelText('Name')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: /type/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /add field/i })).toBeInTheDocument()
  })

  it('blocks submission and shows a message when the name is blank', async () => {
    const onAdd = vi.fn()
    render(<AddEntityForm onAdd={onAdd} onCancel={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: 'Add entity' }))

    expect(await screen.findByText('Name is required')).toBeInTheDocument()
    expect(onAdd).not.toHaveBeenCalled()
  })

  it('adds and removes field rows', async () => {
    render(<AddEntityForm onAdd={vi.fn()} onCancel={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: /add field/i }))
    expect(screen.getByLabelText('Field 1 name')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /remove field 1/i }))
    expect(screen.queryByLabelText('Field 1 name')).not.toBeInTheDocument()
  })

  it('calls onAdd with the assembled entity and drops blank-keyed rows', async () => {
    const onAdd = vi.fn()
    render(<AddEntityForm onAdd={onAdd} onCancel={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('Name'), 'Madam Eva')
    await userEvent.click(screen.getByRole('button', { name: /add field/i }))
    await userEvent.type(screen.getByLabelText('Field 1 name'), 'role')
    await userEvent.type(screen.getByLabelText('Field 1 value'), 'seer')
    // A second, blank-keyed row that must be dropped.
    await userEvent.click(screen.getByRole('button', { name: /add field/i }))
    await userEvent.type(screen.getByLabelText('Field 2 value'), 'orphaned')

    await userEvent.click(screen.getByRole('button', { name: 'Add entity' }))

    expect(onAdd).toHaveBeenCalledWith({
      entityType: 'actor',
      name: 'Madam Eva',
      fields: { role: 'seer' },
    })
  })

  it('blocks submission and shows a banner on duplicate field keys', async () => {
    const onAdd = vi.fn()
    render(<AddEntityForm onAdd={onAdd} onCancel={vi.fn()} />)

    await userEvent.type(screen.getByLabelText('Name'), 'Madam Eva')
    await userEvent.click(screen.getByRole('button', { name: /add field/i }))
    await userEvent.type(screen.getByLabelText('Field 1 name'), 'role')
    await userEvent.type(screen.getByLabelText('Field 1 value'), 'seer')
    await userEvent.click(screen.getByRole('button', { name: /add field/i }))
    await userEvent.type(screen.getByLabelText('Field 2 name'), 'role')
    await userEvent.type(screen.getByLabelText('Field 2 value'), 'fortune teller')

    await userEvent.click(screen.getByRole('button', { name: 'Add entity' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/duplicate field "role"/i)
    expect(onAdd).not.toHaveBeenCalled()
  })

  it('calls onCancel when cancelled', async () => {
    const onCancel = vi.fn()
    render(<AddEntityForm onAdd={vi.fn()} onCancel={onCancel} />)

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onCancel).toHaveBeenCalled()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<AddEntityForm onAdd={vi.fn()} onCancel={vi.fn()} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
