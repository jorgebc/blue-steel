import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { NewEntityCard } from './NewEntityCard'
import type { NewDiffCard } from '@/types/session'

const card: NewDiffCard = {
  cardId: 'n1',
  cardType: 'NEW',
  entityType: 'space',
  name: 'Castle Ravenloft',
  fullProfile: { region: 'Barovia', kind: 'fortress' },
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('NewEntityCard', () => {
  it('renders the name and the full profile fields', () => {
    render(
      <NewEntityCard
        card={card}
        decision={{ action: 'accept' }}
        onSetDecision={vi.fn()}
        onEdit={vi.fn()}
      />
    )

    expect(screen.getByText('Castle Ravenloft')).toBeInTheDocument()
    expect(screen.getByText('region')).toBeInTheDocument()
    expect(screen.getByText('Barovia')).toBeInTheDocument()
    expect(screen.getByText('kind')).toBeInTheDocument()
    expect(screen.getByText('fortress')).toBeInTheDocument()
  })

  it('emits an edit click through onEdit', async () => {
    const onEdit = vi.fn()
    render(
      <NewEntityCard
        card={card}
        decision={{ action: 'accept' }}
        onSetDecision={vi.fn()}
        onEdit={onEdit}
      />
    )

    await userEvent.click(screen.getByRole('button', { name: 'Edit' }))

    expect(onEdit).toHaveBeenCalledOnce()
  })

  it('emits a delete decision when Delete is clicked', async () => {
    const onSetDecision = vi.fn()
    render(
      <NewEntityCard
        card={card}
        decision={{ action: 'accept' }}
        onSetDecision={onSetDecision}
        onEdit={vi.fn()}
      />
    )

    await userEvent.click(screen.getByRole('button', { name: 'Delete' }))

    expect(onSetDecision).toHaveBeenCalledWith({ action: 'delete' })
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <NewEntityCard
        card={card}
        decision={{ action: 'accept' }}
        onSetDecision={vi.fn()}
        onEdit={vi.fn()}
      />
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
