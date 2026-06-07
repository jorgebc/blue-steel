package com.bluesteel.application.port.out.query;

import com.bluesteel.application.model.ingestion.EntityContext;
import java.util.List;
import java.util.UUID;

/**
 * Driven port for Query Mode context retrieval: returns the top-N committed entity-version
 * snapshots across all four entity types whose embeddings are most similar to a question embedding,
 * scoped to a campaign (D-062, D-034). Versions without an embedding row are excluded (D-063).
 * Decoupled from the Stage-1 ingestion similarity path. Never use Spring AI {@code VectorStore} —
 * native SQL only (ARCH-04).
 */
public interface QueryContextRetrievalPort {

  /**
   * Retrieves the most relevant world-state context for a question within a campaign.
   *
   * @param campaignId scopes the search to this campaign's committed sessions
   * @param queryEmbedding the embedding of the user's question
   * @param topN maximum number of entity snapshots to return (context bound, D-034)
   */
  List<EntityContext> retrieveRelevantContext(UUID campaignId, float[] queryEmbedding, int topN);
}
