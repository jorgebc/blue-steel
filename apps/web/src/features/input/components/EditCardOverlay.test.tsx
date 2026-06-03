import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { EditCardOverlay } from './EditCardOverlay'
import type { ExistingDiffCard, NewDiffCard } from '@/types/session'

const card: ExistingDiffCard = {
  cardId: 'e1',
  cardType: 'EXISTING',
  entityId: 'ent-1',
  entityType: 'actor',
  name: 'Strahd',
  changedFields: { title: 'Count' },
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('EditCardOverlay', () => {
  it('renders an input per editable field seeded with its current value', () => {
    render(<EditCardOverlay card={card} open onClose={vi.fn()} onSave={vi.fn()} />)

    expect(screen.getByLabelText('title')).toHaveValue('Count')
  })

  it('renders nothing when closed', () => {
    render(<EditCardOverlay card={card} open={false} onClose={vi.fn()} onSave={vi.fn()} />)

    expect(screen.queryByLabelText('title')).not.toBeInTheDocument()
  })

  it('emits the edited fields on Save', async () => {
    const onSave = vi.fn()
    render(<EditCardOverlay card={card} open onClose={vi.fn()} onSave={onSave} />)

    const input = screen.getByLabelText('title')
    await userEvent.clear(input)
    await userEvent.type(input, 'Lord')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(onSave).toHaveBeenCalledWith({ title: 'Lord' })
  })

  it('round-trips a numeric field as a number', async () => {
    const numericCard: ExistingDiffCard = {
      cardId: 'e2',
      cardType: 'EXISTING',
      entityId: 'ent-2',
      entityType: 'actor',
      name: 'Rahadin',
      changedFields: { level: 12 },
    }
    const onSave = vi.fn()
    render(<EditCardOverlay card={numericCard} open onClose={vi.fn()} onSave={onSave} />)

    const input = screen.getByLabelText('level')
    expect(input).toHaveValue('12')
    await userEvent.clear(input)
    await userEvent.type(input, '13')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(onSave).toHaveBeenCalledWith({ level: 13 })
  })

  it('round-trips an object field as an object', async () => {
    const objectCard: NewDiffCard = {
      cardId: 'n2',
      cardType: 'NEW',
      entityType: 'space',
      name: 'Hidden Vault',
      fullProfile: { coords: { x: 1, y: 2 } },
    }
    const onSave = vi.fn()
    render(<EditCardOverlay card={objectCard} open onClose={vi.fn()} onSave={onSave} />)

    expect(screen.getByLabelText('coords')).toHaveValue('{"x":1,"y":2}')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(onSave).toHaveBeenCalledWith({ coords: { x: 1, y: 2 } })
  })

  it('shows an array field as a comma-separated list and round-trips it to a string[]', async () => {
    const aliasCard: ExistingDiffCard = {
      cardId: 'e3',
      cardType: 'EXISTING',
      entityId: 'ent-3',
      entityType: 'actor',
      name: 'Strahd',
      changedFields: { aliases: ['The Ancient', 'The Devil'] },
    }
    const onSave = vi.fn()
    render(<EditCardOverlay card={aliasCard} open onClose={vi.fn()} onSave={onSave} />)

    const input = screen.getByLabelText('aliases')
    expect(input).toHaveValue('The Ancient, The Devil')
    await userEvent.clear(input)
    await userEvent.type(input, 'The Ancient, Lord of Barovia')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(onSave).toHaveBeenCalledWith({ aliases: ['The Ancient', 'Lord of Barovia'] })
  })

  it('renders a long-text field (description) as a textarea', () => {
    const descCard: ExistingDiffCard = {
      cardId: 'e4',
      cardType: 'EXISTING',
      entityId: 'ent-4',
      entityType: 'actor',
      name: 'Strahd',
      changedFields: { description: 'A brooding vampire lord.' },
    }
    render(<EditCardOverlay card={descCard} open onClose={vi.fn()} onSave={vi.fn()} />)

    expect(screen.getByLabelText('description').tagName).toBe('TEXTAREA')
  })

  it('closes without saving on Cancel', async () => {
    const onClose = vi.fn()
    const onSave = vi.fn()
    render(<EditCardOverlay card={card} open onClose={onClose} onSave={onSave} />)

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(onClose).toHaveBeenCalledOnce()
    expect(onSave).not.toHaveBeenCalled()
  })

  it('closes without saving on Escape', async () => {
    const onClose = vi.fn()
    const onSave = vi.fn()
    render(<EditCardOverlay card={card} open onClose={onClose} onSave={onSave} />)

    await userEvent.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalled()
    expect(onSave).not.toHaveBeenCalled()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <EditCardOverlay card={card} open onClose={vi.fn()} onSave={vi.fn()} />
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
