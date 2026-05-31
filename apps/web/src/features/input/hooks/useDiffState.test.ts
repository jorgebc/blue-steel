import { describe, it, expect } from 'vitest'
import { act, renderHook } from '@testing-library/react'
import { isEdit, useDiffState } from './useDiffState'
import type {
  ConflictCard,
  DiffPayload,
  ExistingDiffCard,
  NewDiffCard,
  UncertainDiffCard,
} from '@/types/session'

const existing: ExistingDiffCard = {
  cardId: 'e1',
  cardType: 'EXISTING',
  entityId: 'ent-e1',
  entityType: 'actor',
  name: 'Strahd',
  changedFields: { title: 'Count' },
}

const created: NewDiffCard = {
  cardId: 'n1',
  cardType: 'NEW',
  entityType: 'space',
  name: 'Castle Ravenloft',
  fullProfile: { region: 'Barovia' },
}

const uncertain: UncertainDiffCard = {
  cardId: 'u1',
  cardType: 'UNCERTAIN',
  entityType: 'actor',
  extractedMention: 'the old woman',
  candidateEntityId: 'ent-cand',
  candidateEntityName: 'Madam Eva',
}

const conflict: ConflictCard = {
  conflictId: 'k1',
  entityId: 'ent-e1',
  entityType: 'actor',
  description: 'Title contradiction',
  extractedFact: 'King',
  existingFact: 'Count',
}

function makeDiff(overrides: Partial<DiffPayload> = {}): DiffPayload {
  return {
    narrativeSummaryHeader: 'A dark night in Barovia.',
    actors: [existing, uncertain],
    spaces: [created],
    events: [],
    relations: [],
    detectedConflicts: [conflict],
    ...overrides,
  }
}

describe('useDiffState', () => {
  it('defaults non-UNCERTAIN cards to accept and leaves UNCERTAIN cards undecided', () => {
    const { result } = renderHook(() => useDiffState(makeDiff()))

    expect(result.current.decisions.get('e1')).toEqual({ action: 'accept' })
    expect(result.current.decisions.get('n1')).toEqual({ action: 'accept' })
    expect(result.current.decisions.has('u1')).toBe(false)
  })

  it('starts with the unresolved and unacknowledged counts reflecting the payload', () => {
    const { result } = renderHook(() => useDiffState(makeDiff()))

    expect(result.current.unresolvedUncertainCount).toBe(1)
    expect(result.current.unacknowledgedConflictCount).toBe(1)
  })

  it('records an edit decision for a card', () => {
    const { result } = renderHook(() => useDiffState(makeDiff()))

    act(() => result.current.setDecision('e1', { action: 'edit', editedFields: { title: 'Lord' } }))

    const decision = result.current.decisions.get('e1')!
    expect(isEdit(decision)).toBe(true)
    expect(decision).toEqual({ action: 'edit', editedFields: { title: 'Lord' } })
  })

  it('drives unresolvedUncertainCount to zero once the UNCERTAIN card is resolved', () => {
    const { result } = renderHook(() => useDiffState(makeDiff()))

    act(() =>
      result.current.resolveUncertain({
        cardId: 'u1',
        resolution: 'MATCH',
        matchedEntityId: 'ent-cand',
      })
    )

    expect(result.current.unresolvedUncertainCount).toBe(0)
    expect(result.current.uncertainResolutions.get('u1')).toEqual({
      cardId: 'u1',
      resolution: 'MATCH',
      matchedEntityId: 'ent-cand',
    })
  })

  it('drives unacknowledgedConflictCount to zero once the conflict is acknowledged', () => {
    const { result } = renderHook(() => useDiffState(makeDiff()))

    act(() => result.current.acknowledgeConflict('k1'))

    expect(result.current.unacknowledgedConflictCount).toBe(0)
    expect(result.current.acknowledgedConflicts.has('k1')).toBe(true)
  })

  it('reports zero counts when the diff has no UNCERTAIN cards or conflicts', () => {
    const { result } = renderHook(() =>
      useDiffState(makeDiff({ actors: [existing], detectedConflicts: [] }))
    )

    expect(result.current.unresolvedUncertainCount).toBe(0)
    expect(result.current.unacknowledgedConflictCount).toBe(0)
  })
})
