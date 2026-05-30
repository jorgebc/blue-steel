package com.bluesteel.adapters.out.ai;

import com.bluesteel.application.model.ingestion.ConflictWarning;
import com.bluesteel.application.model.ingestion.EntityContext;
import com.bluesteel.application.model.ingestion.ExtractionResult;
import com.bluesteel.application.port.out.ingestion.ConflictDetectionPort;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Stub for the real Spring AI conflict-detection adapter. Active on {@code llm-real} or {@code
 * llm-ollama} profiles; pgvector retrieval + ChatClient logic is implemented in F2.6.
 */
@Component
@Profile("llm-real | llm-ollama")
public class SpringAiConflictDetectionAdapter implements ConflictDetectionPort {

  @Override
  public List<ConflictWarning> detect(
      ExtractionResult extraction, List<EntityContext> relevantContext) {
    throw new UnsupportedOperationException("Real LLM adapter not implemented until F2.6");
  }
}
