import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { axe } from 'vitest-axe'
import { RelationsList } from './RelationsList'
import type { Relation } from '@/types/relation'

function relation(over: Partial<Relation>): Relation {
  return {
    relationId: 'r1',
    name: 'a bond',
    kind: null,
    sourceEntityId: null,
    sourceEntityType: null,
    targetEntityId: null,
    targetEntityType: null,
    sessionId: 's1',
    ...over,
  }
}

const nameById = { a1: 'Mira', s1: 'Thornwick' }

describe('RelationsList', () => {
  it('renders the empty state when there are no relations', () => {
    render(<RelationsList relations={[]} nameById={nameById} />)

    expect(screen.getByText(/no relations yet/i)).toBeInTheDocument()
  })

  it('describes a resolved relation as source → kind → target', () => {
    render(
      <RelationsList
        relations={[relation({ kind: 'alliance', sourceEntityId: 'a1', targetEntityId: 's1' })]}
        nameById={nameById}
      />
    )

    const item = screen.getByRole('listitem')
    expect(item).toHaveTextContent('Mira')
    expect(item).toHaveTextContent('alliance')
    expect(item).toHaveTextContent('Thornwick')
  })

  it('falls back to the relation name when kind is null', () => {
    render(
      <RelationsList
        relations={[relation({ name: 'rivalry', sourceEntityId: 'a1', targetEntityId: 's1' })]}
        nameById={nameById}
      />
    )

    expect(screen.getByRole('listitem')).toHaveTextContent('rivalry')
  })

  it('shows Unknown for an unresolved endpoint', () => {
    render(
      <RelationsList
        relations={[relation({ sourceEntityId: 'a1', targetEntityId: null })]}
        nameById={nameById}
      />
    )

    expect(screen.getByRole('listitem')).toHaveTextContent('Unknown')
  })

  it('calls onSelect with the relation id when an item is clicked', async () => {
    const onSelect = vi.fn()
    render(
      <RelationsList
        relations={[
          relation({
            relationId: 'r1',
            kind: 'alliance',
            sourceEntityId: 'a1',
            targetEntityId: 's1',
          }),
        ]}
        nameById={nameById}
        onSelect={onSelect}
      />
    )

    await userEvent.click(screen.getByRole('button', { name: /show annotations/i }))
    expect(onSelect).toHaveBeenCalledWith('r1')
  })

  it('marks the selected relation as pressed', () => {
    render(
      <RelationsList
        relations={[
          relation({
            relationId: 'r1',
            kind: 'alliance',
            sourceEntityId: 'a1',
            targetEntityId: 's1',
          }),
        ]}
        nameById={nameById}
        selectedId="r1"
      />
    )

    expect(screen.getByRole('button', { name: /show annotations/i })).toHaveAttribute(
      'aria-pressed',
      'true'
    )
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <RelationsList
        relations={[relation({ kind: 'alliance', sourceEntityId: 'a1', targetEntityId: 's1' })]}
        nameById={nameById}
      />
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
