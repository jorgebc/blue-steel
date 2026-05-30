package com.bluesteel.application.model.session;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.UUID;

/**
 * Diff card for a mention the LLM could not confidently classify as MATCH or NEW (D-042). Must be
 * resolved by the GM before the diff can be committed.
 */
@JsonTypeName("UNCERTAIN")
public record UncertainEntityCard(
    UUID cardId, String entityType, String extractedMention, UUID candidateEntityId)
    implements DiffCard {}
