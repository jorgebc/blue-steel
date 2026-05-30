package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Real embedding adapter using Spring AI {@link EmbeddingModel}. Active on {@code llm-real} or
 * {@code llm-ollama} profiles; the active model is auto-configured per profile in {@link AiConfig}
 * (D-088, D-093). ERROR-logs and rethrows on provider failure so pipeline callers can transition
 * sessions to {@code FAILED} (LOG-02).
 */
@Component
@Profile("llm-real | llm-ollama")
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

  private static final Logger log = LoggerFactory.getLogger(SpringAiEmbeddingAdapter.class);

  private final EmbeddingModel embeddingModel;

  public SpringAiEmbeddingAdapter(EmbeddingModel embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  @Override
  public float[] embed(String content) {
    try {
      return embeddingModel.embed(content);
    } catch (Exception e) {
      log.error("Embedding generation failed for content length={}", content.length(), e);
      throw e;
    }
  }
}
