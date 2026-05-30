package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.port.out.ingestion.NarrativeExtractionPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub for the real Spring AI narrative-extraction adapter. Active on {@code llm-real} or {@code
 * llm-ollama} profiles; real {@code ChatClient} logic is wired in F2.4.
 */
@Component
@Profile("llm-real | llm-ollama")
public class SpringAiNarrativeExtractionAdapter implements NarrativeExtractionPort {

  @Override
  public ExtractionResult extract(String rawSummaryText) {
    throw new UnsupportedOperationException("Real LLM adapter not implemented until F2.4");
  }
}
