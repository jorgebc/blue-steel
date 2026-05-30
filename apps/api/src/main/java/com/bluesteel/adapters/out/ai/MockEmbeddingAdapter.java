package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Returns a deterministic {@code float[1536]} vector — index 0 = 1.0f, all others 0.0f (zero API
 * cost). Suitable for unit and integration tests that depend on embedding generation without a live
 * LLM provider.
 */
@Component
@Profile("!llm-real & !llm-ollama")
public class MockEmbeddingAdapter implements EmbeddingPort {

  static final int EMBEDDING_DIMENSION = 1536;

  @Override
  public float[] embed(String content) {
    float[] vector = new float[EMBEDDING_DIMENSION];
    vector[0] = 1.0f;
    return vector;
  }
}
