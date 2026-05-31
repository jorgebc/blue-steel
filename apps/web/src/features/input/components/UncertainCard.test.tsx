import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { UncertainCard } from './UncertainCard'
import type { UncertainDiffCard } from '@/types/session'

const card: UncertainDiffCard = {
  cardId: 'u1',
  cardType: 'UNCERTAIN',
  entityType: 'actor',
  extractedMention: 'the old fortune teller',
  candidateEntityId: 'ent-eva',
  candidateEntityName: 'Madam Eva',
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('UncertainCard', () => {
  it('shows the extracted mention and candidate name', () => {
    render(<UncertainCard card={card} resolution={undefined} onResolve={vi.fn()} />)

    expect(screen.getByText('the old fortune teller')).toBeInTheDocument()
    expect(screen.getAllByText('Madam Eva').length).toBeGreaterThan(0)
    expect(screen.getByText('Requires Resolution')).toBeInTheDocument()
  })

  it('resolves to MATCH with the candidate id when "Same entity" is chosen', async () => {
    const onResolve = vi.fn()
    render(<UncertainCard card={card} resolution={undefined} onResolve={onResolve} />)

    await userEvent.click(screen.getByRole('radio', { name: /same entity/i }))

    expect(onResolve).toHaveBeenCalledWith({
      cardId: 'u1',
      resolution: 'MATCH',
      matchedEntityId: 'ent-eva',
    })
  })

  it('resolves to NEW with a null id when "Different entity" is chosen', async () => {
    const onResolve = vi.fn()
    render(<UncertainCard card={card} resolution={undefined} onResolve={onResolve} />)

    await userEvent.click(screen.getByRole('radio', { name: /different entity/i }))

    expect(onResolve).toHaveBeenCalledWith({
      cardId: 'u1',
      resolution: 'NEW',
      matchedEntityId: null,
    })
  })

  it('reflects an existing MATCH resolution as the checked radio', () => {
    render(
      <UncertainCard
        card={card}
        resolution={{ cardId: 'u1', resolution: 'MATCH', matchedEntityId: 'ent-eva' }}
        onResolve={vi.fn()}
      />
    )

    expect(screen.getByRole('radio', { name: /same entity/i })).toBeChecked()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <UncertainCard card={card} resolution={undefined} onResolve={vi.fn()} />
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
