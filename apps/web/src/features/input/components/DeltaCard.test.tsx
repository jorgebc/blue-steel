import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { DeltaCard } from './DeltaCard'
import type { ExistingDiffCard } from '@/types/session'

const card: ExistingDiffCard = {
  cardId: 'e1',
  cardType: 'EXISTING',
  entityId: 'ent-1',
  entityType: 'actor',
  name: 'Strahd von Zarovich',
  changedFields: { title: 'Count', allegiance: 'Barovia' },
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('DeltaCard', () => {
  it('renders the name and only the changed fields', () => {
    render(
      <DeltaCard
        card={card}
        decision={{ action: 'accept' }}
        onSetDecision={vi.fn()}
        onEdit={vi.fn()}
      />
    )

    expect(screen.getByText('Strahd von Zarovich')).toBeInTheDocument()
    expect(screen.getByText('title')).toBeInTheDocument()
    expect(screen.getByText('Count')).toBeInTheDocument()
    expect(screen.getByText('allegiance')).toBeInTheDocument()
  })

  it('emits an accept decision when Accept is clicked', async () => {
    const onSetDecision = vi.fn()
    render(
      <DeltaCard
        card={card}
        decision={{ action: 'delete' }}
        onSetDecision={onSetDecision}
        onEdit={vi.fn()}
      />
    )

    await userEvent.click(screen.getByRole('button', { name: 'Accept' }))

    expect(onSetDecision).toHaveBeenCalledWith({ action: 'accept' })
  })

  it('emits a delete decision when Delete is clicked', async () => {
    const onSetDecision = vi.fn()
    render(
      <DeltaCard
        card={card}
        decision={{ action: 'accept' }}
        onSetDecision={onSetDecision}
        onEdit={vi.fn()}
      />
    )

    await userEvent.click(screen.getByRole('button', { name: 'Delete' }))

    expect(onSetDecision).toHaveBeenCalledWith({ action: 'delete' })
  })

  it('invokes onEdit when Edit is clicked', async () => {
    const onEdit = vi.fn()
    render(
      <DeltaCard
        card={card}
        decision={{ action: 'accept' }}
        onSetDecision={vi.fn()}
        onEdit={onEdit}
      />
    )

    await userEvent.click(screen.getByRole('button', { name: 'Edit' }))

    expect(onEdit).toHaveBeenCalledOnce()
  })

  it('marks the Accept button pressed when the decision is accept', () => {
    render(
      <DeltaCard
        card={card}
        decision={{ action: 'accept' }}
        onSetDecision={vi.fn()}
        onEdit={vi.fn()}
      />
    )

    expect(screen.getByRole('button', { name: 'Accept' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <DeltaCard
        card={card}
        decision={{ action: 'accept' }}
        onSetDecision={vi.fn()}
        onEdit={vi.fn()}
      />
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
