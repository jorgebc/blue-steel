import { useCallback, useMemo, useReducer } from 'react'
import type { AddedEntityPayload, DiffCard, DiffPayload } from '@/types/session'

/** Per-card decision held in client state (the commit-payload shape is derived later, F2.11). */
export type CardDecision =
  | { action: 'accept' }
  | { action: 'edit'; editedFields: Record<string, unknown> }
  | { action: 'delete' }

/** Resolution of an UNCERTAIN card: link to the candidate (MATCH) or create a new entity (NEW). */
export type UncertainResolution =
  | { cardId: string; resolution: 'MATCH'; matchedEntityId: string }
  | { cardId: string; resolution: 'NEW'; matchedEntityId: null }

/** Narrows a {@link CardDecision} to the `edit` variant. */
export function isEdit(d: CardDecision): d is Extract<CardDecision, { action: 'edit' }> {
  return d.action === 'edit'
}

interface State {
  decisions: Map<string, CardDecision>
  uncertainResolutions: Map<string, UncertainResolution>
  acknowledgedConflicts: Set<string>
  // Reviewer-added entities the extraction missed, keyed by a client-generated id (F6.2, D-053).
  addedEntities: Map<string, AddedEntityPayload>
}

type Action =
  | { type: 'setDecision'; cardId: string; decision: CardDecision }
  | { type: 'resolveUncertain'; resolution: UncertainResolution }
  | { type: 'acknowledgeConflict'; conflictId: string }
  | { type: 'addEntity'; id: string; entity: AddedEntityPayload }
  | { type: 'removeAddedEntity'; id: string }

function allCards(diff: DiffPayload): DiffCard[] {
  return [...diff.actors, ...diff.spaces, ...diff.events, ...diff.relations]
}

function init(diff: DiffPayload): State {
  const decisions = new Map<string, CardDecision>()
  for (const card of allCards(diff)) {
    // UNCERTAIN cards have no default — they must be explicitly resolved (D-042).
    if (card.cardType !== 'UNCERTAIN') decisions.set(card.cardId, { action: 'accept' })
  }
  return {
    decisions,
    uncertainResolutions: new Map(),
    acknowledgedConflicts: new Set(),
    addedEntities: new Map(),
  }
}

function reducer(state: State, action: Action): State {
  switch (action.type) {
    case 'setDecision': {
      const decisions = new Map(state.decisions)
      decisions.set(action.cardId, action.decision)
      return { ...state, decisions }
    }
    case 'resolveUncertain': {
      const uncertainResolutions = new Map(state.uncertainResolutions)
      uncertainResolutions.set(action.resolution.cardId, action.resolution)
      return { ...state, uncertainResolutions }
    }
    case 'acknowledgeConflict': {
      const acknowledgedConflicts = new Set(state.acknowledgedConflicts)
      acknowledgedConflicts.add(action.conflictId)
      return { ...state, acknowledgedConflicts }
    }
    case 'addEntity': {
      const addedEntities = new Map(state.addedEntities)
      addedEntities.set(action.id, action.entity)
      return { ...state, addedEntities }
    }
    case 'removeAddedEntity': {
      const addedEntities = new Map(state.addedEntities)
      addedEntities.delete(action.id)
      return { ...state, addedEntities }
    }
  }
}

/**
 * Single source of truth for every per-card user decision in the diff review.
 * Initialized from a {@link DiffPayload} (non-UNCERTAIN cards default to `accept`).
 * The derived counts drive the commit guard (D-042, D-033).
 */
export function useDiffState(diff: DiffPayload) {
  const [state, dispatch] = useReducer(reducer, diff, init)

  const setDecision = useCallback(
    (cardId: string, decision: CardDecision) => dispatch({ type: 'setDecision', cardId, decision }),
    []
  )
  const resolveUncertain = useCallback(
    (resolution: UncertainResolution) => dispatch({ type: 'resolveUncertain', resolution }),
    []
  )
  const acknowledgeConflict = useCallback(
    (conflictId: string) => dispatch({ type: 'acknowledgeConflict', conflictId }),
    []
  )
  // The client id is generated here (not in the reducer) to keep the reducer pure; it lives only
  // as a Map key for list rendering/removal and is dropped when the commit payload is assembled.
  const addEntity = useCallback(
    (entity: AddedEntityPayload) =>
      dispatch({ type: 'addEntity', id: crypto.randomUUID(), entity }),
    []
  )
  const removeAddedEntity = useCallback(
    (id: string) => dispatch({ type: 'removeAddedEntity', id }),
    []
  )

  const uncertainCardIds = useMemo(
    () =>
      allCards(diff)
        .filter((c) => c.cardType === 'UNCERTAIN')
        .map((c) => c.cardId),
    [diff]
  )

  const unresolvedUncertainCount = useMemo(
    () => uncertainCardIds.filter((id) => !state.uncertainResolutions.has(id)).length,
    [uncertainCardIds, state.uncertainResolutions]
  )

  const unacknowledgedConflictCount = useMemo(
    () =>
      diff.detectedConflicts.filter((c) => !state.acknowledgedConflicts.has(c.conflictId)).length,
    [diff.detectedConflicts, state.acknowledgedConflicts]
  )

  return {
    decisions: state.decisions,
    uncertainResolutions: state.uncertainResolutions,
    acknowledgedConflicts: state.acknowledgedConflicts,
    addedEntities: state.addedEntities,
    setDecision,
    resolveUncertain,
    acknowledgeConflict,
    addEntity,
    removeAddedEntity,
    unresolvedUncertainCount,
    unacknowledgedConflictCount,
  }
}
