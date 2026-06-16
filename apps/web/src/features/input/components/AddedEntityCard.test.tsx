import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { AddedEntityCard } from './AddedEntityCard'
import type { AddedEntityPayload } from '@/types/session'

const entity: AddedEntityPayload = {
  entityType: 'actor',
  name: 'Madam Eva',
  fields: { role: 'seer', allegiance: 'neutral' },
}

describe('AddedEntityCard', () => {
  it('renders the name, type, and fields', () => {
    render(<AddedEntityCard entity={entity} onRemove={vi.fn()} />)

    expect(screen.getByText('Madam Eva')).toBeInTheDocument()
    expect(screen.getByText('actor')).toBeInTheDocument()
    expect(screen.getByText('role')).toBeInTheDocument()
    expect(screen.getByText('seer')).toBeInTheDocument()
  })

  it('shows a placeholder when there are no fields', () => {
    render(<AddedEntityCard entity={{ ...entity, fields: {} }} onRemove={vi.fn()} />)

    expect(screen.getByText('No profile fields.')).toBeInTheDocument()
  })

  it('calls onRemove when the remove button is clicked', async () => {
    const onRemove = vi.fn()
    render(<AddedEntityCard entity={entity} onRemove={onRemove} />)

    await userEvent.click(screen.getByRole('button', { name: /remove/i }))

    expect(onRemove).toHaveBeenCalled()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(<AddedEntityCard entity={entity} onRemove={vi.fn()} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
