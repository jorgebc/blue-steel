package com.bluesteel.application.model.embedding;

import java.util.UUID;

/** Value object carrying a single embedding row to be inserted into {@code entity_embeddings}. */
public record EntityEmbeddingRow(
    String entityType,
    UUID entityId,
    UUID entityVersionId,
    UUID sessionId,
    float[] embedding,
    String contentHash) {}
