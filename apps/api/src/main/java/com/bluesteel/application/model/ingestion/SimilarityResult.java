package com.bluesteel.application.model.ingestion;

import java.util.UUID;

/**
 * A candidate world-state entity returned by Stage-1 pgvector similarity search (D-041). Contains
 * the entity's persistence projection plus the cosine similarity score. The use case
 * (EntityResolutionService) maps the non-similarity fields into an {@link EntityContext} before any
 * LLM call — adapters never construct {@code EntityContext} directly (ARCHITECTURE §6.2).
 */
public record SimilarityResult(
    UUID entityId,
    String entityType,
    String name,
    String stateSnapshot,
    UUID sessionId,
    int versionNumber,
    double similarity) {}
