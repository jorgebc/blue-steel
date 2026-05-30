package com.bluesteel.application.model.session;

import java.util.UUID;

/**
 * Non-blocking warning card surfaced in the diff when an extracted entity contradicts established
 * world-state facts (D-033). The commit is never blocked by conflicts — the user decides.
 */
public record ConflictCard(
    UUID conflictId,
    UUID entityId,
    String entityType,
    String description,
    String extractedFact,
    String existingFact) {}
