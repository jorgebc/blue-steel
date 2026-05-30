package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.port.out.embedding.EmbeddingPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub for the real Spring AI embedding adapter. Active on {@code llm-real} or {@code llm-ollama}
 * profiles; real {@code EmbeddingModel} (Gemini or Ollama) logic is wired in F2.6.
 */
@Component
@Profile("llm-real | llm-ollama")
public class SpringAiEmbeddingAdapter implements EmbeddingPort {

  @Override
  public float[] embed(String content) {
    throw new UnsupportedOperationException("Real LLM adapter not implemented until F2.6");
  }
}
