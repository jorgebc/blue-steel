import { describe, it, expect } from 'vitest'
import { buildCommitPayload, type DiffStateSlice } from './useCommitPayload'
import type { CardDecision, UncertainResolution } from './useDiffState'
import type {
  AddedEntityPayload,
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
    narrativeSummaryHeader: 'A dark night.',
    actors: [existing, uncertain],
    spaces: [created],
    events: [],
    relations: [],
    detectedConflicts: [conflict],
    ...overrides,
  }
}

function makeState(overrides: Partial<DiffStateSlice> = {}): DiffStateSlice {
  return {
    decisions: new Map<string, CardDecision>(),
    uncertainResolutions: new Map<string, UncertainResolution>(),
    acknowledgedConflicts: new Set<string>(),
    addedEntities: new Map<string, AddedEntityPayload>(),
    ...overrides,
  }
}

describe('buildCommitPayload', () => {
  it('emits an accept decision for every non-UNCERTAIN card and skips UNCERTAIN cards', () => {
    const payload = buildCommitPayload(makeDiff(), makeState())

    expect(payload.cardDecisions).toEqual([
      { cardId: 'e1', action: 'accept' },
      { cardId: 'n1', action: 'accept' },
    ])
  })

  it('includes editedFields only for edit decisions', () => {
    const decisions = new Map<string, CardDecision>([
      ['e1', { action: 'edit', editedFields: { title: 'Lord' } }],
      ['n1', { action: 'delete' }],
    ])

    const payload = buildCommitPayload(makeDiff(), makeState({ decisions }))

    expect(payload.cardDecisions).toEqual([
      { cardId: 'e1', action: 'edit', editedFields: { title: 'Lord' } },
      { cardId: 'n1', action: 'delete' },
    ])
  })

  it('maps a MATCH resolution to the candidate id and a NEW resolution to null', () => {
    const matchState = makeState({
      uncertainResolutions: new Map<string, UncertainResolution>([
        ['u1', { cardId: 'u1', resolution: 'MATCH', matchedEntityId: 'ent-cand' }],
      ]),
    })
    const newState = makeState({
      uncertainResolutions: new Map<string, UncertainResolution>([
        ['u1', { cardId: 'u1', resolution: 'NEW', matchedEntityId: null }],
      ]),
    })

    expect(buildCommitPayload(makeDiff(), matchState).uncertainResolutions).toEqual([
      { cardId: 'u1', resolution: 'MATCH', matchedEntityId: 'ent-cand' },
    ])
    expect(buildCommitPayload(makeDiff(), newState).uncertainResolutions).toEqual([
      { cardId: 'u1', resolution: 'NEW', matchedEntityId: null },
    ])
  })

  it('maps every acknowledged conflict id', () => {
    const state = makeState({ acknowledgedConflicts: new Set(['k1']) })

    const payload = buildCommitPayload(makeDiff(), state)

    expect(payload.acknowledgedConflicts).toEqual([{ conflictId: 'k1' }])
  })

  it('produces empty arrays when nothing is decided or detected', () => {
    const payload = buildCommitPayload(
      makeDiff({ actors: [], spaces: [], detectedConflicts: [] }),
      makeState()
    )

    expect(payload.cardDecisions).toEqual([])
    expect(payload.uncertainResolutions).toEqual([])
    expect(payload.acknowledgedConflicts).toEqual([])
    expect(payload.addedEntities).toEqual([])
  })

  it('emits reviewer-added entities and drops their client-only ids', () => {
    const addedEntities = new Map<string, AddedEntityPayload>([
      ['client-1', { entityType: 'actor', name: 'Madam Eva', fields: { role: 'seer' } }],
      ['client-2', { entityType: 'space', name: 'Tser Pool', fields: {} }],
    ])

    const payload = buildCommitPayload(makeDiff(), makeState({ addedEntities }))

    expect(payload.addedEntities).toEqual([
      { entityType: 'actor', name: 'Madam Eva', fields: { role: 'seer' } },
      { entityType: 'space', name: 'Tser Pool', fields: {} },
    ])
  })
})
