package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractedMention;
import com.bluesteel.application.model.ingestion.ResolvedEntity;
import com.bluesteel.application.port.out.ingestion.EntityResolutionPort;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub for the real Spring AI entity-resolution adapter. Active on {@code llm-real} or {@code
 * llm-ollama} profiles; pgvector Stage 1 + ChatClient Stage 2 logic is implemented in F2.5.
 */
@Component
@Profile("llm-real | llm-ollama")
public class SpringAiEntityResolutionAdapter implements EntityResolutionPort {

  @Override
  public List<ResolvedEntity> resolve(
      List<ExtractedMention> mentions, List<EntityContext> candidateContext) {
    throw new UnsupportedOperationException("Real LLM adapter not implemented until F2.5");
  }
}
