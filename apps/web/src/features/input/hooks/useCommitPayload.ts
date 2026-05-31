import type {
  CardDecisionPayload,
  CommitPayload,
  DiffCard,
  DiffPayload,
  UncertainResolutionPayload,
} from '@/types/session'
import { isEdit, type CardDecision, type UncertainResolution } from './useDiffState'

/** The slice of {@link useDiffState}'s return that the commit payload is derived from. */
export interface DiffStateSlice {
  decisions: Map<string, CardDecision>
  uncertainResolutions: Map<string, UncertainResolution>
  acknowledgedConflicts: Set<string>
}

function nonUncertainCards(diff: DiffPayload): DiffCard[] {
  return [...diff.actors, ...diff.spaces, ...diff.events, ...diff.relations].filter(
    (c) => c.cardType !== 'UNCERTAIN'
  )
}

/**
 * Translates the live diff + decision state into the §7.6 commit payload. Every
 * non-UNCERTAIN card gets an explicit decision (default `accept`, D-080);
 * `editedFields` is emitted only for `edit` decisions (no `add` action, D-053).
 */
export function buildCommitPayload(diff: DiffPayload, state: DiffStateSlice): CommitPayload {
  const cardDecisions: CardDecisionPayload[] = nonUncertainCards(diff).map((card) => {
    const decision = state.decisions.get(card.cardId) ?? { action: 'accept' }
    return isEdit(decision)
      ? { cardId: card.cardId, action: 'edit', editedFields: decision.editedFields }
      : { cardId: card.cardId, action: decision.action }
  })

  const uncertainResolutions: UncertainResolutionPayload[] = [
    ...state.uncertainResolutions.values(),
  ].map((r) => ({
    cardId: r.cardId,
    resolution: r.resolution,
    matchedEntityId: r.resolution === 'MATCH' ? r.matchedEntityId : null,
  }))

  const acknowledgedConflicts = [...state.acknowledgedConflicts].map((conflictId) => ({
    conflictId,
  }))

  return { cardDecisions, uncertainResolutions, acknowledgedConflicts }
}
