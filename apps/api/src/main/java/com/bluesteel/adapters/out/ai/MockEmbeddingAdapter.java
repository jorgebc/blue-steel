package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Returns a deterministic vector — index 0 = 1.0f, all others 0.0f (zero API cost) — sized to the
 * profile's configured embedding dimension so the mock output always matches the live {@code
 * entity_embeddings.embedding} column (1536 under {@code llm-real}, 1024 locally; D-088, D-093).
 * Sizing it from the same {@code embeddingDimension} property that parameterises the Liquibase
 * column keeps the two in lock-step and prevents an async post-commit dimension-mismatch insert
 * failure on the default mock profile. Suitable for unit and integration tests that depend on
 * embedding generation without a live LLM provider.
 */
@Component
@Profile("!llm-real & !llm-ollama")
public class MockEmbeddingAdapter implements EmbeddingPort {

  private final int dimension;

  public MockEmbeddingAdapter(
      @Value("${spring.liquibase.parameters.embeddingDimension:1536}") int dimension) {
    this.dimension = dimension;
  }

  @Override
  public float[] embed(String content) {
    float[] vector = new float[dimension];
    vector[0] = 1.0f;
    return vector;
  }
}
