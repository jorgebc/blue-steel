package com.bluesteel.application.model.ingestion;

import java.util.UUID;

/**
 * The resolution result for one {@link ExtractedMention}: the outcome classification and, when
 * {@code outcome == MATCH}, the id of the matched world-state entity.
 *
 * <p>{@code matchedEntityId} is non-null only for {@link ResolutionOutcome#MATCH}; it is {@code
 * null} for {@link ResolutionOutcome#NEW} and {@link ResolutionOutcome#UNCERTAIN}.
 */
public record ResolvedEntity(
    ExtractedMention mention, ResolutionOutcome outcome, UUID matchedEntityId) {}
